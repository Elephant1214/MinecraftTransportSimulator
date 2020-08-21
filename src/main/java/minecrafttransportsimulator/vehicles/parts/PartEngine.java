package minecrafttransportsimulator.vehicles.parts;

import mcinterface.InterfaceAudio;
import mcinterface.InterfaceNetwork;
import mcinterface.InterfaceRender;
import mcinterface.WrapperNBT;
import minecrafttransportsimulator.MTS;
import minecrafttransportsimulator.baseclasses.Damage;
import minecrafttransportsimulator.baseclasses.Point3d;
import minecrafttransportsimulator.baseclasses.Point3i;
import minecrafttransportsimulator.jsondefs.JSONPart;
import minecrafttransportsimulator.jsondefs.JSONPart.PartEngine.EngineSound;
import minecrafttransportsimulator.jsondefs.JSONVehicle.VehiclePart;
import minecrafttransportsimulator.packets.instances.PacketPlayerChatMessage;
import minecrafttransportsimulator.packets.instances.PacketVehiclePartEngine;
import minecrafttransportsimulator.packets.instances.PacketVehiclePartEngine.Signal;
import minecrafttransportsimulator.rendering.components.IVehiclePartFXProvider;
import minecrafttransportsimulator.rendering.instances.ParticleDrip;
import minecrafttransportsimulator.rendering.instances.ParticleFlame;
import minecrafttransportsimulator.rendering.instances.ParticleSmoke;
import minecrafttransportsimulator.sound.SoundInstance;
import minecrafttransportsimulator.systems.ConfigSystem;
import minecrafttransportsimulator.vehicles.main.EntityVehicleF_Physics;

public class PartEngine extends APart implements IVehiclePartFXProvider{
	
	//State data.
	public boolean isCreative;
	public boolean oilLeak;
	public boolean fuelLeak;
	public boolean brokenStarter;
	public byte reverseGears;
	public byte currentGear;
	public double hours;
	public double rpm;
	public double temp = 20;
	public double pressure;
	public EngineStates state = EngineStates.ENGINE_OFF;
	
	//Runtime calculated values.
	public double fuelFlow;
	public PartEngine linkedEngine;
	
	//Internal variables.
	private boolean startSounds;
	private boolean backfired;
	private boolean isPropellerInLiquid;
	private byte starterLevel;
	private byte shiftCooldown;
	private int internalFuel;
	private long lastTimeParticleSpawned;
	private float currentGearRatio;
	private float propellerGearboxRatio;
	private double lowestWheelVelocity;
	private double desiredWheelVelocity;
	private double propellerAxialVelocity;
	private double engineAxialVelocity;
	private float wheelFriction;
	private double ambientTemp;
	private double coolingFactor;
	private double engineTargetRPM;
	private double engineRotation;
	private double prevEngineRotation;
	private double driveshaftRotation;
	private double prevDriveshaftRotation;
	
	//Constants and static variables.
	private final int startRPM;
	private final int stallRPM;
	private static final float COLD_TEMP = 30F;
	private static final float OVERHEAT_TEMP_1 = 115.556F;
	private static final float OVERHEAT_TEMP_2 = 121.111F;
	private static final float FAILURE_TEMP = 132.222F;
	private static final float LOW_OIL_PRESSURE = 40F;
	
	
	public PartEngine(EntityVehicleF_Physics vehicle, VehiclePart packVehicleDef, JSONPart definition, WrapperNBT data){
		super(vehicle, packVehicleDef, definition, data);
		this.isCreative = data.getBoolean("isCreative");
		this.oilLeak = data.getBoolean("oilLeak");
		this.fuelLeak = data.getBoolean("fuelLeak");
		this.brokenStarter = data.getBoolean("brokenStarter");
		this.currentGear = (byte) data.getInteger("currentGear");
		this.hours = data.getDouble("hours");
		this.rpm = data.getDouble("rpm");
		this.temp = data.getDouble("temp");
		this.pressure = data.getDouble("pressure");
		this.state = EngineStates.values()[data.getInteger("state")];
		this.startSounds = vehicle.world.isClient();
		for(float gear : definition.engine.gearRatios){
			if(gear < 0){
				++reverseGears;
			}
		}
		this.startRPM = definition.engine.maxRPM < 15000 ? 500 : 2000;
		this.stallRPM = definition.engine.maxRPM < 15000 ? 300 : 1500;
		
		//If we are on an aircraft, set our gear to 1 as aircraft don't have shifters.
		//Well, except blimps, but that's a special case.
		if(vehicle.definition.general.isAircraft){
			currentGear = 1;
		}
	}
	
	@Override
	public void attack(Damage damage){
		if(damage.isExplosion){
			hours += damage.amount*20*ConfigSystem.configObject.general.engineHoursFactor.value;
			if(!definition.engine.isSteamPowered){
				if(!oilLeak)oilLeak = Math.random() < ConfigSystem.configObject.damage.engineLeakProbability.value*10;
				if(!fuelLeak)fuelLeak = Math.random() < ConfigSystem.configObject.damage.engineLeakProbability.value*10;
				if(!brokenStarter)brokenStarter = Math.random() < 0.05;
			}
			InterfaceNetwork.sendToClientsTracking(new PacketVehiclePartEngine(this, damage.amount*10*ConfigSystem.configObject.general.engineHoursFactor.value, oilLeak, fuelLeak, brokenStarter), vehicle);
		}else{
			hours += damage.amount*2*ConfigSystem.configObject.general.engineHoursFactor.value;
			if(!definition.engine.isSteamPowered){
				if(!oilLeak)oilLeak = Math.random() < ConfigSystem.configObject.damage.engineLeakProbability.value;
				if(!fuelLeak)fuelLeak = Math.random() < ConfigSystem.configObject.damage.engineLeakProbability.value;
			}
			InterfaceNetwork.sendToClientsTracking(new PacketVehiclePartEngine(this, damage.amount*ConfigSystem.configObject.general.engineHoursFactor.value, oilLeak, fuelLeak, brokenStarter), vehicle);
		}
	}
	
	@Override
	public void update(){
		super.update();
		//Set current gear ratio based on current gear.
		currentGearRatio = definition.engine.gearRatios[currentGear + reverseGears];
		
		//Start up sounds if we haven't already.  We don't do this during construction as other mods are
		//PITA and will construct new vehicles every tick to get data.  I'm looking a YOU The One Probe!
		if(startSounds && state.running && vehicle.world.isClient()){
			if(definition.engine.customSoundset != null){
				for(EngineSound soundDefinition : definition.engine.customSoundset){
					InterfaceAudio.playQuickSound(new SoundInstance(this, soundDefinition.soundName, true));
				}
			}else{
				InterfaceAudio.playQuickSound(new SoundInstance(this, definition.packID + ":" + definition.systemName + "_running", true));
				InterfaceAudio.playQuickSound(new SoundInstance(this, definition.packID + ":" + definition.systemName + "_supercharger", true));
			}
			startSounds = false;
		}
				
		//Check to see if we are linked and need to equalize power between us and another engine.
		if(linkedEngine != null){
			if(linkedEngine.worldPos.distanceTo(this.worldPos) > 16){
				linkedEngine.linkedEngine = null;
				linkedEngine = null;
				if(vehicle.world.isClient()){
					InterfaceNetwork.sendToClientsNear(new PacketPlayerChatMessage("interact.jumpercable.linkdropped"), vehicle.world.getDimensionID(), new Point3i(worldPos), 16);
				}
			}else if(vehicle.electricPower + 0.5 < linkedEngine.vehicle.electricPower){
				linkedEngine.vehicle.electricPower -= 0.005F;
				vehicle.electricPower += 0.005F;
			}else if(vehicle.electricPower > linkedEngine.vehicle.electricPower + 0.5){
				vehicle.electricPower -= 0.005F;
				linkedEngine.vehicle.electricPower += 0.005F;
			}else{
				linkedEngine.linkedEngine = null;
				linkedEngine = null;
				if(vehicle.world.isClient()){
					InterfaceNetwork.sendToClientsNear(new PacketPlayerChatMessage("interact.jumpercable.powerequal"), vehicle.world.getDimensionID(), new Point3i(worldPos), 16);
				}
			}
		}
		
		//Add cooling for ambient temp.
		ambientTemp = 25*vehicle.world.getTemperature(new Point3i(vehicle.position)) - 5*(Math.pow(2, vehicle.position.y/400) - 1);
		coolingFactor = 0.001 - ((definition.engine.superchargerEfficiency/1000F)*(rpm/2000F)) + vehicle.velocity/500F;
		temp -= (temp - ambientTemp)*coolingFactor;
		
		//Check to see if electric or hand starter can keep running.
		if(state.esOn){
			if(starterLevel == 0){
				if(vehicle.electricPower > 2){
					starterLevel += 4;
				}else{
					setElectricStarterStatus(false);
				}
			}
			if(starterLevel > 0){
				if(!isCreative){
					vehicle.electricUsage += 0.05F;
				}
				if(vehicle.fuel > getTotalFuelConsumption()*ConfigSystem.configObject.general.fuelUsageFactor.value && !isCreative){
					vehicle.fuel -= getTotalFuelConsumption()*ConfigSystem.configObject.general.fuelUsageFactor.value;
					fuelFlow += getTotalFuelConsumption()*ConfigSystem.configObject.general.fuelUsageFactor.value;
				}
			}
		}else if(state.hsOn){
			if(starterLevel == 0){
				if(state.running){
					state = EngineStates.RUNNING;
				}else{
					state = state.magnetoOn ? EngineStates.MAGNETO_ON_STARTERS_OFF : EngineStates.ENGINE_OFF;
				}
			}
		}
		
		//If the starter is running, adjust RPM.
		if(starterLevel > 0){
			--starterLevel;
			if(rpm < startRPM*1.2){
				rpm = Math.min(rpm + definition.engine.starterPower, startRPM*1.2);
			}else{
				rpm = Math.max(rpm - definition.engine.starterPower, startRPM*1.2);
			}
		}
		
		//Do running logic.
		if(state.running){
			//Provide electric power to the vehicle we're in.
			vehicle.electricUsage -= 0.05*rpm/definition.engine.maxRPM;
			
			//Add hours to the engine.
			if(!isCreative){
				hours += 0.001*getTotalWearFactor();
				
				//Add extra hours if we are running the engine too fast.
				if(rpm > getSafeRPMFromMax(definition.engine.maxRPM)){
					hours += 0.001*(rpm - getSafeRPMFromMax(definition.engine.maxRPM))/10F*getTotalWearFactor();
				}
			}
			
			//Do engine-type specific update logic.
			if(definition.engine.isSteamPowered){
				//TODO do steam engine logic.
			}else{
				//Try to get fuel from the vehicle and calculate fuel flow.
				if(!isCreative && !vehicle.fluidName.isEmpty()){
					if(!ConfigSystem.configObject.fuel.fuels.containsKey(definition.engine.fuelType)){					
						throw new IllegalArgumentException("ERROR: Engine:" + definition.packID + ":" + definition.systemName + " wanted fuel configs for fuel of type:" + definition.engine.fuelType + ", but these do not exist in the config file.  Fuels currently in the file are:" + ConfigSystem.configObject.fuel.fuels.keySet().toString() + "If you are on a server, this means the server and client configs are not the same.  If this is a modpack, TELL THE AUTHOR IT IS BORKEN!");
					}else if(!ConfigSystem.configObject.fuel.fuels.get(definition.engine.fuelType).containsKey(vehicle.fluidName)){
						//Clear out the fuel from this vehicle as it's the wrong type.
						vehicle.fuel = 0;
						vehicle.fluidName = "";
					}else{
						fuelFlow = getTotalFuelConsumption()*ConfigSystem.configObject.general.fuelUsageFactor.value/ConfigSystem.configObject.fuel.fuels.get(definition.engine.fuelType).get(vehicle.fluidName)*rpm*(fuelLeak ? 1.5F : 1.0F)/definition.engine.maxRPM;
						vehicle.fuel -= fuelFlow;
					}
				}
				
				//Add temp based on engine speed.
				temp += Math.max(0, (7*rpm/definition.engine.maxRPM - temp/(COLD_TEMP*2))/20);
				
				//Adjust oil pressure based on RPM and leak status.
				pressure = Math.min(90 - temp/10, pressure + rpm/startRPM - 0.5*(oilLeak ? 5F : 1F)*(pressure/LOW_OIL_PRESSURE));
							
				//Add extra hours and temp if we have low oil.
				if(pressure < LOW_OIL_PRESSURE){
					temp += Math.max(0, (20*rpm/definition.engine.maxRPM)/20);
					hours += 0.01*getTotalWearFactor();
				}
				
				//Add extra hours if we tried to run the engine fast without it being warmed up.
				if(rpm > startRPM*1.5 && temp < COLD_TEMP){
					hours += 0.001*(rpm/startRPM - 1)*getTotalWearFactor();
				}
				
				//Add extra hours, and possibly explode the engine, if its too hot.
				if(temp > OVERHEAT_TEMP_1){
					hours += 0.001*(temp - OVERHEAT_TEMP_1)*getTotalWearFactor();
					if(temp > FAILURE_TEMP && !vehicle.world.isClient() && !isCreative){
						explodeEngine();
					}
				}
				
				//If the engine has high hours, give a chance for a backfire.
				if(hours > 200 && !vehicle.world.isClient()){
					if(Math.random() < hours/10000*(getSafeRPMFromMax(this.definition.engine.maxRPM)/(rpm+getSafeRPMFromMax(this.definition.engine.maxRPM)/2))){
						backfireEngine();
					}
				}
				
				//Check if we need to stall the engine for various conditions.
				if(!vehicle.world.isClient()){
					if(!vehicle.world.isClient() && isInLiquid()){
						stallEngine(Signal.DROWN);
					}else if(vehicle.fuel == 0 && !isCreative){
						stallEngine(Signal.FUEL_OUT);
					}else if(rpm < stallRPM){
						stallEngine(Signal.TOO_SLOW);
					}
				}
			}
			
			//Do automatic transmission functions if needed.
			if(definition.engine.isAutomatic){
				if(currentGear > 0){
					if(shiftCooldown == 0){
						if(definition.engine.upShiftRPM != null && definition.engine.downShiftRPM != null){
							if(rpm > definition.engine.upShiftRPM[currentGear - 1]*0.5*(1.0F + vehicle.throttle/100F)) {
								shiftUp(false);
								shiftCooldown = definition.engine.shiftSpeed;
							}else if(rpm < definition.engine.downShiftRPM[currentGear - 1]*0.5*(1.0F + vehicle.throttle/100F) && currentGear > 1){
								shiftDown(false);
								shiftCooldown = definition.engine.shiftSpeed;
							}
						}else{
							if(rpm > getSafeRPMFromMax(definition.engine.maxRPM)*0.5F*(1.0F + vehicle.throttle/100F)){
								shiftUp(false);
								shiftCooldown = definition.engine.shiftSpeed;
							}else if(rpm < getSafeRPMFromMax(definition.engine.maxRPM)*0.25*(1.0F + vehicle.throttle/100F) && currentGear > 1){
								shiftDown(false);
								shiftCooldown = definition.engine.shiftSpeed;
							}
						}
					}else{
						--shiftCooldown;
					}
				}
			}
		}else{
			//If we aren't a steam engine, set pressure and fuel flow to 0.
			if(!definition.engine.isSteamPowered){
				pressure = 0;
				fuelFlow = 0;
			}
			
			//Internal fuel is used for engine sound wind down.  NOT used for power.
			if(internalFuel > 0){
				--internalFuel;
				if(rpm < startRPM){
					internalFuel = 0;
				}
			}
			
			//Start engine if the RPM is high enough to cause it to start by itself.
			//Used for drowned engines that come out of the water, or engines that don't
			//have the ability to engage a starter.
			if(rpm > startRPM){
				if(vehicle.fuel > 0 || isCreative){
					if(!isInLiquid() && state.magnetoOn && !vehicle.world.isClient()){
						startEngine();
					}
				}
			}
		}
		
		//Update engine RPM.  This depends on what is connected.
		//First check to see if we need to check driven wheels.  Only for cars.
		//While doing this we also get the friction those wheels are providing.
		//This is used later in force calculations.
		if(vehicle.definition.car != null){
			lowestWheelVelocity = 999F;
			desiredWheelVelocity = -999F;
			wheelFriction = 0;
			engineTargetRPM = !state.esOn ? vehicle.throttle/100F*(definition.engine.maxRPM - startRPM/1.25 - hours) + startRPM/1.25 : startRPM*1.2;
			
			//Update wheel friction and velocity.
			for(PartGroundDevice wheel : vehicle.wheels){
				if((wheel.placementOffset.z > 0 && vehicle.definition.car.isFrontWheelDrive) || (wheel.placementOffset.z <= 0 && vehicle.definition.car.isRearWheelDrive)){
					//If we have grounded wheels, and this wheel is not on the ground, don't take it into account.
					//This means the wheel is spinning in the air and can't provide force or feedback.
					if(wheel.isOnGround()){
						wheelFriction += wheel.getMotiveFriction() - wheel.getFrictionLoss();
						lowestWheelVelocity = Math.min(wheel.angularVelocity, lowestWheelVelocity);
						desiredWheelVelocity = Math.max(wheel.getDesiredAngularVelocity(), desiredWheelVelocity);
					}
				}
			}
			
			//Adjust RPM of the engine to wheels.
			if(currentGearRatio != 0 && starterLevel == 0){
				//Don't adjust it down to stall the engine, that can only be done via backfire.
				if(wheelFriction > 0){
					double desiredRPM = lowestWheelVelocity*1200F*currentGearRatio*vehicle.definition.car.axleRatio;
					rpm += (desiredRPM - rpm)/10D;
					if(rpm < stallRPM && state.running){
						rpm = stallRPM;
					}
				}else{
					//No wheel force.  Adjust wheels to engine speed.
					for(PartGroundDevice wheel : vehicle.wheels){
						wheel.skipAngularCalcs = false;
						if((wheel.placementOffset.z > 0 && vehicle.definition.car.isFrontWheelDrive) || (wheel.placementOffset.z <= 0 && vehicle.definition.car.isRearWheelDrive)){
							if(currentGearRatio != 0){
								wheel.angularVelocity = (float) rpm/currentGearRatio/vehicle.definition.car.axleRatio/60F/20F;
							}else if(wheel.angularVelocity > 0){
								wheel.angularVelocity = Math.max(0, wheel.angularVelocity - 0.01F);
							}else{
								wheel.angularVelocity = Math.min(0, wheel.angularVelocity + 0.01F);
							}
						}
					}
				}
			}
		}
		
		//Update propeller variables.
		boolean havePropeller = false;
		for(APart part : childParts){
			if(part instanceof PartPropeller){
				PartPropeller propeller = (PartPropeller) part;
				havePropeller = true;
				Point3d propellerThrustAxis = new Point3d(0D, 0D, 1D).rotateCoarse(propeller.totalRotation.copy().add(vehicle.angles));
				propellerAxialVelocity = vehicle.normalizedVelocityVector.copy().multiply(vehicle.velocity).dotProduct(propellerThrustAxis);
				
				//If wheel friction is 0, and we aren't in neutral, get RPM contributions for that.
				if(wheelFriction == 0 && currentGearRatio != 0){
					isPropellerInLiquid = propeller.isInLiquid();
					propellerGearboxRatio = definition.engine.propellerRatio != 0 ? definition.engine.propellerRatio : currentGearRatio;
					double propellerForcePenalty = Math.max(0, (propeller.definition.propeller.diameter - 75)/(50*(definition.engine.fuelConsumption + (definition.engine.superchargerFuelConsumption*definition.engine.superchargerEfficiency)) - 15));
					double propellerDesiredSpeed = 0.0254*propeller.currentPitch*rpm/propellerGearboxRatio*Math.signum(currentGearRatio)/60D/20D;
					double propellerFeedback = (propellerDesiredSpeed - propellerAxialVelocity)*(isPropellerInLiquid ? 130 : 40);
					if(currentGearRatio < 0 || propeller.currentPitch < 0){
						propellerFeedback *= -1;
					}
					propellerFeedback += propellerForcePenalty*50;
					
					if(state.running){
						double engineTargetRPM = vehicle.throttle/100F*(definition.engine.maxRPM - startRPM*1.25 - hours) + startRPM*1.25;
						double engineRPMDifference = engineTargetRPM - rpm;
						
						//propellerFeedback can't make an engine stall, but hours can.
						if(rpm + engineRPMDifference/10 > stallRPM && rpm + engineRPMDifference/10 - propellerFeedback < stallRPM){
							rpm = stallRPM;
						}else{
							rpm += engineRPMDifference/10 - propellerFeedback;
						}
						//System.out.format("AxialSpeed:%f, DesiredSpeed:%f TargetRPM:%f ActualRPM:%f ForcePenalty:%f Feedback:%f NetRPMReduction:%f\n", propellerAxialVelocity, propellerDesiredSpeed, engineTargetRPM, rpm, propellerForcePenalty, propellerFeedback, engineRPMDifference/10 - propellerFeedback);
					}else{
						rpm -= (propellerFeedback - propellerForcePenalty*50);
					}
				}
			}
		}
		
		//If wheel friction is 0, and we don't have a propeller, or we're in neutral, adjust RPM to throttle position.
		//Or, if we are not on, just slowly spin the engine down.
		if((wheelFriction == 0 && !havePropeller) || currentGearRatio == 0){
			if(state.running){
				double engineTargetRPM = vehicle.throttle/100F*(definition.engine.maxRPM - startRPM*1.25 - hours*10) + startRPM*1.25;
				rpm += (engineTargetRPM - rpm)/10;
				if(rpm > getSafeRPMFromMax(definition.engine.maxRPM) && definition.engine.jetPowerFactor == 0){
					rpm -= Math.abs(engineTargetRPM - rpm)/5;
				}
			}else if(!state.esOn && !state.hsOn){
				rpm = Math.max(rpm - 10, 0);
			}
		}
		
		///Update variables used for jet thrust.
		if(definition.engine.jetPowerFactor > 0){
			Point3d engineThrustAxis = new Point3d(0D, 0D, 1D).rotateCoarse(totalRotation.copy().add(vehicle.angles));
			engineAxialVelocity = vehicle.normalizedVelocityVector.copy().multiply(vehicle.velocity).dotProduct(engineThrustAxis);
			
			//Check for entities forward and aft of the engine and damage them.
			if(!vehicle.world.isClient() && rpm >= 5000){
				boundingBox.widthRadius += 0.25;
				boundingBox.heightRadius += 0.25;
				boundingBox.depthRadius += 0.25;
				boundingBox.globalCenter.add(vehicle.headingVector);
				Damage jetIntake = new Damage("jet_intake", definition.engine.jetPowerFactor*ConfigSystem.configObject.damage.jetDamageFactor.value*rpm/1000F, boundingBox, vehicle.getController());
				vehicle.world.attackEntities(jetIntake, vehicle);
				
				boundingBox.globalCenter.subtract(vehicle.headingVector);
				boundingBox.globalCenter.subtract(vehicle.headingVector);
				Damage jetExhaust = new Damage("jet_exhaust", definition.engine.jetPowerFactor*ConfigSystem.configObject.damage.jetDamageFactor.value*rpm/2000F, boundingBox, jetIntake.attacker).setFire();
				vehicle.world.attackEntities(jetExhaust, vehicle);
				
				boundingBox.globalCenter.add(vehicle.headingVector);
				boundingBox.widthRadius -= 0.25;
				boundingBox.heightRadius -= 0.25;
				boundingBox.depthRadius -= 0.25;
			}
		}
		
		//Update engine and driveshaft rotation.
		//If we are on a car, the driveshaft needs to follow the wheel rotation, not our own.
		prevEngineRotation = engineRotation;
		engineRotation += 360D*rpm/1200D;
		prevDriveshaftRotation = driveshaftRotation;
		if(vehicle.definition.car != null){
			double driveShaftDesiredSpeed = Double.MIN_VALUE;
			for(PartGroundDevice wheel : vehicle.wheels){
				if((wheel.placementOffset.z > 0 && vehicle.definition.car.isFrontWheelDrive) || (wheel.placementOffset.z <= 0 && vehicle.definition.car.isRearWheelDrive)){
					driveShaftDesiredSpeed = Math.max(Math.abs(wheel.angularVelocity), driveShaftDesiredSpeed);
				}
			}
			driveshaftRotation += vehicle.SPEED_FACTOR*driveShaftDesiredSpeed*Math.signum(vehicle.groundVelocity)*360D;
		}else{
			driveshaftRotation += 360D*rpm/1200D*definition.engine.gearRatios[currentGear + reverseGears];
		}
	}
	
	@Override
	public void remove(){
		super.remove();
		//Set state to off and tell wheels to stop skipping calcs from being controlled by the engine.
		state = EngineStates.ENGINE_OFF;
		for(PartGroundDevice wheel : vehicle.wheels){
			if(!wheel.isOnGround() && ((wheel.placementOffset.z > 0 && vehicle.definition.car.isFrontWheelDrive) || (wheel.placementOffset.z <= 0 && vehicle.definition.car.isRearWheelDrive))){
				wheel.skipAngularCalcs = false;
			}
		}
	}
	
	@Override
	public WrapperNBT getData(){
		WrapperNBT data = super.getData();
		data.setBoolean("isCreative", isCreative);
		data.setBoolean("oilLeak", oilLeak);
		data.setBoolean("fuelLeak", fuelLeak);
		data.setBoolean("brokenStarter", brokenStarter);
		data.setInteger("currentGear", currentGear);
		data.setDouble("hours", hours);
		data.setDouble("rpm", rpm);
		data.setDouble("temp", temp);
		data.setDouble("pressure", pressure);
		data.setInteger("state", (byte) state.ordinal());
		return data;
	}
	
	@Override
	public float getWidth(){
		return 1.0F;
	}

	@Override
	public float getHeight(){
		return 1.0F;
	}
	
	
	
	//--------------------START OF ENGINE STATE CHANGE METHODS--------------------
	
	public void setMagnetoStatus(boolean on){
		if(on){
			if(state.equals(EngineStates.MAGNETO_OFF_ES_ON)){
				state = EngineStates.MAGNETO_ON_ES_ON;
			}else if(state.equals(EngineStates.MAGNETO_OFF_HS_ON)){
				state = EngineStates.MAGNETO_ON_HS_ON;
			}else if(state.equals(EngineStates.ENGINE_OFF)){
				state = EngineStates.MAGNETO_ON_STARTERS_OFF;
			}
		}else{
			if(state.equals(EngineStates.MAGNETO_ON_ES_ON)){
				state = EngineStates.MAGNETO_OFF_ES_ON;
			}else if(state.equals(EngineStates.MAGNETO_ON_HS_ON)){
				state = EngineStates.MAGNETO_OFF_HS_ON;
			}else if(state.equals(EngineStates.MAGNETO_ON_STARTERS_OFF)){
				state = EngineStates.ENGINE_OFF;
			}else if(state.equals(EngineStates.RUNNING)){
				state = EngineStates.ENGINE_OFF;
				internalFuel = 100;
				if(vehicle.world.isClient()){
					InterfaceAudio.playQuickSound(new SoundInstance(this, definition.packID + ":" + definition.systemName + "_stopping"));
				}
			}
		}
	}
	
	public void setElectricStarterStatus(boolean engaged){
		if(!brokenStarter){
			if(engaged){
				if(state.equals(EngineStates.ENGINE_OFF)){
					state = EngineStates.MAGNETO_OFF_ES_ON;
				}else if(state.equals(EngineStates.MAGNETO_ON_STARTERS_OFF)){
					state = EngineStates.MAGNETO_ON_ES_ON;
					if(vehicle.world.isClient()){
						InterfaceAudio.playQuickSound(new SoundInstance(this, definition.packID + ":" + definition.systemName + "_cranking", true));
					}
				}else if(state.equals(EngineStates.RUNNING)){
					state =  EngineStates.RUNNING_ES_ON;
					if(vehicle.world.isClient()){
						InterfaceAudio.playQuickSound(new SoundInstance(this, definition.packID + ":" + definition.systemName + "_cranking", true));
					}
				}
			}else{
				if(state.equals(EngineStates.MAGNETO_OFF_ES_ON)){
					state = EngineStates.ENGINE_OFF;
				}else if(state.equals(EngineStates.MAGNETO_ON_ES_ON)){
					state = EngineStates.MAGNETO_ON_STARTERS_OFF;
				}else if(state.equals(EngineStates.RUNNING_ES_ON)){
					state = EngineStates.RUNNING;
				}
			}
		}
	}
	
	public void startEngine(){
		if(state.equals(EngineStates.MAGNETO_ON_STARTERS_OFF)){
			state = EngineStates.RUNNING;
		}else if(state.equals(EngineStates.MAGNETO_ON_ES_ON)){
			state = EngineStates.RUNNING_ES_ON;
		}else if(state.equals(EngineStates.MAGNETO_ON_HS_ON)){
			state = EngineStates.RUNNING;
		}
		
		//Turn starter off.
		starterLevel = 0;
		
		//If we are not a steam engine, set oil pressure.
		if(!definition.engine.isSteamPowered){
			pressure = 60;
		}
		
		//Send off packet and start sounds.
		if(!vehicle.world.isClient()){
			InterfaceNetwork.sendToClientsTracking(new PacketVehiclePartEngine(this, Signal.START), vehicle);
		}else{
			InterfaceAudio.playQuickSound(new SoundInstance(this, definition.packID + ":" + definition.systemName + "_starting"));
			if(definition.engine.customSoundset != null){
				for(EngineSound soundDefinition : definition.engine.customSoundset){
					InterfaceAudio.playQuickSound(new SoundInstance(this, soundDefinition.soundName, true));
				}
			}else{
				InterfaceAudio.playQuickSound(new SoundInstance(this, definition.packID + ":" + definition.systemName + "_running", true));
				InterfaceAudio.playQuickSound(new SoundInstance(this, definition.packID + ":" + definition.systemName + "_supercharger", true));
			}
		}
	}
	
	public void handStartEngine(){
		if(state.equals(EngineStates.ENGINE_OFF)){
			state = EngineStates.MAGNETO_OFF_HS_ON;
		}else if(state.equals(EngineStates.MAGNETO_ON_STARTERS_OFF)){
			state = EngineStates.MAGNETO_ON_HS_ON;
		}else if(state.equals(EngineStates.RUNNING)){
			state = EngineStates.RUNNING_HS_ON;
		}else{
			return;
		}
		
		//Add a small amount to the starter level from the player's hand, and play cranking sound.
		starterLevel += 4;
		if(vehicle.world.isClient()){
			InterfaceAudio.playQuickSound(new SoundInstance(this, definition.packID + ":" + definition.systemName + "_cranking", true));
		}
	}
	
	public void stallEngine(Signal signal){
		if(state.equals(EngineStates.RUNNING)){
			state = EngineStates.MAGNETO_ON_STARTERS_OFF;
		}else if(state.equals(EngineStates.RUNNING_ES_ON)){
			state = EngineStates.MAGNETO_ON_ES_ON;
		}else if(state.equals(EngineStates.RUNNING_HS_ON)){
			state = EngineStates.MAGNETO_ON_HS_ON;
		}
		
		//If we stalled due to not drowning, set internal fuel to play wind-down sounds.
		if(vehicle.world.isClient()){
			if(!signal.equals(Signal.DROWN)){
				internalFuel = 100;
			}
		}
		
		//Send off packet and play stopping sound.
		if(!vehicle.world.isClient()){
			InterfaceNetwork.sendToClientsTracking(new PacketVehiclePartEngine(this, signal), vehicle);
		}else{
			InterfaceAudio.playQuickSound(new SoundInstance(this, definition.packID + ":" + definition.systemName + "_stopping"));
		}
	}
	
	public void backfireEngine(){
		//Decrease RPM and send off packet to have clients do the same.
		//This also causes particles to spawn and sounds to play.
		rpm -= definition.engine.maxRPM < 15000 ? 100 : 500;
		if(!vehicle.world.isClient()){
			InterfaceNetwork.sendToClientsTracking(new PacketVehiclePartEngine(this, Signal.BACKFIRE), vehicle);
		}else{
			InterfaceAudio.playQuickSound(new SoundInstance(this, definition.packID + ":" + definition.systemName + "_sputter"));
			backfired = true;
		}
	}
	
	protected void explodeEngine(){
		if(ConfigSystem.configObject.damage.explosions.value){
			vehicle.world.spawnExplosion(vehicle, worldPos, 1F, true);
		}else{
			vehicle.world.spawnExplosion(vehicle, worldPos, 0F, false);
		}
		isValid = false;
	}
	
	
	
	//--------------------START OF ENGINE GEAR METHODS--------------------
	
	public float getGearshiftRotation(){
		return definition.engine.isAutomatic ? Math.min(1, currentGear)*15F : currentGear*5;
	}
	
	public float getGearshiftPosition_Vertical(){
		if(currentGear < 0){
			return definition.engine.gearRatios.length%2 == 0 ? 15 : -15; 
		}else if(currentGear == 0){
			return 0;
		}else{
			return currentGear%2 == 0 ? -15 : 15;
		}
	}
	
	public float getGearshiftPosition_Horizontal(){
		int columns = (definition.engine.gearRatios.length)/2;
		int firstColumnAngle = columns/2*-5;
		float columnAngleDelta = columns != 1 ? -firstColumnAngle*2/(columns - 1) : 0; 
		if(currentGear < 0){
			return -firstColumnAngle;
		}else if(currentGear == 0){
			return 0;
		}else{
			//Divide the currentGear-1 by two to get our column (0 for column 1, 1 for 2).
			//Then add multiply that by columnAngleDelta to get our delta for this column.
			//Return that value, plus the initial angle.
			return firstColumnAngle + (currentGear - 1)/2*columnAngleDelta;
		}
	}
	
	public void shiftUp(boolean packet){
		if(currentGear < 0){
			++currentGear;
		}else if(currentGear == 0){
			if(vehicle.velocity < 0.25 || wheelFriction == 0){
				currentGear = 1;
			}else if(vehicle.world.isClient()){
				InterfaceAudio.playQuickSound(new SoundInstance(this, MTS.MODID + ":engine_shifting_grinding"));
			}
		}else if(currentGear < definition.engine.gearRatios.length - (1 + reverseGears)){
			if(definition.engine.isAutomatic && packet){
				if(currentGear < 1){
					currentGear = 1;
				}
			}else{
				++currentGear;
			}
		}
	}
	
	public void shiftDown(boolean packet){
		if(currentGear > 0){
			if(definition.engine.isAutomatic && packet){
				currentGear = 0;
			}else{
				--currentGear;
			}
		}else if(currentGear == 0){
			if(vehicle.velocity < 0.25 || wheelFriction == 0){
				currentGear = -1;
				//If the engine is running, and we are a big truck, turn on the backup beeper.
				if(state.running && vehicle.definition.car != null && vehicle.definition.car.isBigTruck && vehicle.world.isClient()){
					InterfaceAudio.playQuickSound(new SoundInstance(this, MTS.MODID + ":backup_beeper", true));
				}
			}else if(vehicle.world.isClient()){
				InterfaceAudio.playQuickSound(new SoundInstance(this, MTS.MODID + ":engine_shifting_grinding"));
			}
		}else if(currentGear + reverseGears > 0){
			--currentGear;
		}
	}
	
	
	
	//--------------------START OF ENGINE PROPERTY METHODS--------------------
	
	public static int getSafeRPMFromMax(int maxRPM){
		return maxRPM < 15000 ? maxRPM - (maxRPM - 2500)/2 : (int) (maxRPM/1.1);
	}
	
	public float getTotalFuelConsumption(){
		return definition.engine.fuelConsumption + definition.engine.superchargerFuelConsumption;
	}
	
	public double getTotalWearFactor(){
		if(definition.engine.superchargerEfficiency > 1.0F){
			return definition.engine.superchargerEfficiency*ConfigSystem.configObject.general.engineHoursFactor.value;
		}else{
			return ConfigSystem.configObject.general.engineHoursFactor.value;
		}
	}
	
	public double getEngineRotation(float partialTicks){
		return engineRotation + (engineRotation - prevEngineRotation)*partialTicks;
	}
	
	public double getDriveshaftRotation(float partialTicks){
		return driveshaftRotation + (driveshaftRotation - prevDriveshaftRotation)*partialTicks;
	}
	
	public Point3d getForceOutput(){
		//Get all the forces this part can output.
		Point3d engineForce = new Point3d(0D, 0D, 0D);
		
		//First get wheel forces, if we have friction to do so.
		if(wheelFriction != 0){
			double wheelForce = 0;
			//If running, use the friction of the wheels to determine the new speed.
			if(state.running || state.esOn){
				wheelForce = (engineTargetRPM - rpm)/definition.engine.maxRPM*currentGearRatio*vehicle.definition.car.axleRatio*(definition.engine.fuelConsumption + (definition.engine.superchargerFuelConsumption*definition.engine.superchargerEfficiency))*0.6F*30F;
				//Check to see if the wheels need to spin out.
				//If they do, we'll need to provide less force.
				if(Math.abs(wheelForce/300F) > wheelFriction || (Math.abs(lowestWheelVelocity) - Math.abs(desiredWheelVelocity) > 0.1 && Math.abs(lowestWheelVelocity) - Math.abs(desiredWheelVelocity) < Math.abs(wheelForce/300F))){
					wheelForce *= vehicle.currentMass/100000F*wheelFriction/Math.abs(wheelForce/300F);					
					for(PartGroundDevice wheel : vehicle.wheels){
						if((wheel.placementOffset.z > 0 && vehicle.definition.car.isFrontWheelDrive) || (wheel.placementOffset.z <= 0 && vehicle.definition.car.isRearWheelDrive)){
							if(currentGearRatio > 0){
								if(wheelForce >= 0){
									wheel.angularVelocity = (float) Math.min(engineTargetRPM/1200F/currentGearRatio/vehicle.definition.car.axleRatio, wheel.angularVelocity + 0.01);
								}else{
									wheel.angularVelocity = (float) Math.min(engineTargetRPM/1200F/currentGearRatio/vehicle.definition.car.axleRatio, wheel.angularVelocity - 0.01);
								}
							}else{
								if(wheelForce >= 0){
									wheel.angularVelocity = (float) Math.max(engineTargetRPM/1200F/currentGearRatio/vehicle.definition.car.axleRatio, wheel.angularVelocity - 0.01);
								}else{
									
									wheel.angularVelocity = (float) Math.max(engineTargetRPM/1200F/currentGearRatio/vehicle.definition.car.axleRatio, wheel.angularVelocity + 0.01);
								}
							}
							wheel.skipAngularCalcs = true;
						}
					}
				}else{
					//If we have wheels not on the ground and we drive them, adjust their velocity now.
					for(PartGroundDevice wheel : vehicle.wheels){
						wheel.skipAngularCalcs = false;
						if(!wheel.isOnGround() && ((wheel.placementOffset.z > 0 && vehicle.definition.car.isFrontWheelDrive) || (wheel.placementOffset.z <= 0 && vehicle.definition.car.isRearWheelDrive))){
							wheel.angularVelocity = lowestWheelVelocity;
						}
					}
				}
				
				//Don't let us have negative engine force at low speeds.
				//This causes odd reversing behavior when the engine tries to maintain speed.
				if(((wheelForce < 0 && currentGear > 0) || (wheelForce > 0 && currentGear < 0)) && vehicle.velocity < 0.25){
					wheelForce = 0;
				}
			}else{
				//Not running, do engine braking.
				wheelForce = -rpm/definition.engine.maxRPM*Math.signum(currentGear)*30;
			}
			engineForce.z += wheelForce;
		}else{
			//No wheel force.  Check for propellers to provide force.
			for(APart part : childParts){
				if(part instanceof PartPropeller && state.running && Math.abs(((PartPropeller) part).currentPitch) > 5){
					PartPropeller propeller = (PartPropeller) part;
					//Get the current linear velocity of the propeller, based on our axial velocity.
					double currentLinearVelocity = 20D*propellerAxialVelocity;
					//Get the desired linear velocity of the propeller, based on the current RPM and pitch.
					double desiredLinearVelocity = 0.0254D*propeller.currentPitch*20D*propeller.angularVelocity;
					//Multiply by a factor to get the true desired linear velocity.  This is slightly higher than theoretical ideal.
					desiredLinearVelocity *= (1D*propeller.currentPitch/propeller.definition.propeller.diameter + 0.2D)/(1D*propeller.currentPitch/propeller.definition.propeller.diameter);
					if(desiredLinearVelocity != 0){
						//Get the angle of attack of the propeller.
						//Note pitch velocity is in linear in meters per second, 
						//This means we need to convert it to meters per revolution before we can move on.
						//This gets the angle as a ratio of forward pitch to propeller circumference.
						double angleOfAttack = ((desiredLinearVelocity - currentLinearVelocity)/(rpm/propellerGearboxRatio/60D))/(propeller.definition.propeller.diameter*Math.PI*0.0254D);
						double thrust = vehicle.airDensity
								*Math.PI*Math.pow(0.0254*propeller.definition.propeller.diameter/2D, 2)
								*Math.abs(desiredLinearVelocity)*(desiredLinearVelocity - currentLinearVelocity)
								*Math.pow(propeller.definition.propeller.diameter/2D/Math.abs(propeller.currentPitch) + propeller.definition.propeller.numberBlades/1000D, 1.5)
								/400D;
	
						//System.out.format("Thrust:%f CurrentLV:%f DesiredLV:%f AoA:%f Gearbox:%f\n", thrust, currentLinearVelocity, desiredLinearVelocity, angleOfAttack, propellerGearboxRatio);
						
						//If the angle of attack is greater than 25 degrees (or a ratio of 0.4663), sap power off the propeller for stalling.
						if(Math.abs(angleOfAttack) > 0.4663D){
							thrust *= 0.4663D/Math.abs(angleOfAttack);
						}
						
						//If the propeller is in the water, increase thrust.
						if(isPropellerInLiquid){
							thrust *= 50;
						}
						
						//Add propeller force to total engine force as a vector.
						//Depends on propeller orientation, as upward propellers provide upwards thrust.
						Point3d propellerThrustVector = new Point3d(0D, 0D, thrust);
						if(propeller.definition.propeller.isRotor){
							//Get the X and Y coords of the action rotation for thrust vectoring on rotors.
							Point3d propellerActionRotation = propeller.getActionRotation(0);
							propellerActionRotation.z = 0;
							propellerThrustVector.rotateCoarse(propellerActionRotation); 
						}
						engineForce.add(propellerThrustVector.rotateCoarse(propeller.totalRotation));
					}
				}
			}
		}
		
		//If we provide jet power, add it now.  This may be done with any parts or wheels on the ground.
		//Propellers max out at about 25 force, so use that to determine this force.
		if(definition.engine.jetPowerFactor > 0 && state.running){
			//First we need the air density (sea level 1.225) so we know how much air we are moving.
			//We then multiply that by the RPM and the fuel consumption to get the raw power produced
			//by the core of the engine.  This is speed-independent as the core will ALWAYS accelerate air.
			//Note that due to a lack of jet physics formulas available, this is "hacky math".
			double safeRPMFactor = rpm/getSafeRPMFromMax(definition.engine.maxRPM);
			double coreContribution = Math.max(10*vehicle.airDensity*definition.engine.fuelConsumption*safeRPMFactor - definition.engine.bypassRatio, 0);
			
			//The fan portion is calculated similarly to how propellers are calculated.
			//This takes into account the air density, and relative speed of the engine versus the fan's desired speed.
			//Again, this is "hacky math", as for some reason there's no data on fan pitches.
			//In this case, however, we don't care about the fuelConsumption as that's only used by the core.
			double fanVelocityFactor = (0.0254*250*rpm/60/20 - engineAxialVelocity)/200D;
			double fanContribution = 10*vehicle.airDensity*safeRPMFactor*fanVelocityFactor*definition.engine.bypassRatio;
			double thrust = (vehicle.reverseThrust ? -(coreContribution + fanContribution) : coreContribution + fanContribution)*definition.engine.jetPowerFactor;
			
			//Add the jet force to the engine.  Use the engine rotation to define the power vector.
			engineForce.add(new Point3d(0D, 0D, thrust).rotateCoarse(totalRotation));
		}
		
		//Finally, return the force we calculated.
		return engineForce;
	}

	
	
	//--------------------START OF ENGINE SOUND METHODS--------------------
	
	@Override
	public void updateProviderSound(SoundInstance sound){
		super.updateProviderSound(sound);
		//Adjust cranking sound pitch to match RPM and stop looping if we are done cranking.
		//Adjust running sound to have pitch based on engine RPM.
		if(sound.soundName.endsWith("_cranking")){
			if(!state.esOn && !state.hsOn){
				sound.stop();
			}else{
				if(definition.engine.isCrankingNotPitched){
					sound.pitch = (float) Math.min(1.0F, vehicle.electricPower/10);
				}else{
					sound.pitch = (float) (rpm/startRPM);
				}
			}
		}else if(sound.soundName.endsWith("backup_beeper")){
			//Turn off backup beeper if we are no longer in reverse.
			if(currentGear >= 0){
				sound.stop();
			}
		}else{
			//If we are using a custom soundset, do that logic. Otherwise, do default sound logic.
			if(definition.engine.customSoundset != null){
				for(EngineSound soundDefinition : definition.engine.customSoundset){
					if(sound.soundName.equals(soundDefinition.soundName)){
						if(!state.running && internalFuel == 0){
							sound.stop();
						}else{
							//Interpolate in the form of Y=A*X + B.
							//In this case, B is the idle offset, A is the slope, X is the RPM, and Y is the output.
							double rpmPercentOfMax = Math.max(0, (rpm - startRPM)/definition.engine.maxRPM);
							sound.pitch = (float) ((soundDefinition.pitchMax - soundDefinition.pitchIdle)*rpmPercentOfMax + soundDefinition.pitchIdle);
							sound.volume = (float) ((soundDefinition.volumeMax - soundDefinition.volumeIdle)*rpmPercentOfMax + soundDefinition.volumeIdle);
						}
					}
				}
			}else{
				//Update running and supercharger sounds.
				if(sound.soundName.endsWith("_running")){
					if(!state.running && internalFuel == 0){
						sound.stop();
					}else{
						//Pitch should be 0.35 at idle, with a 0.35 increase for every 2500 RPM, or every 25000 RPM for jet (high-revving) engines by default.
						//For steam engines, pitch is just 1 as it's meant to be the sound of a firebox.
						if(definition.engine.isSteamPowered){
							sound.pitch = 1.0F;
						}else{
							sound.pitch = (float) (0.35*(1 + Math.max(0, (rpm - startRPM))/(definition.engine.maxRPM < 15000 ? 500 : 5000)));
						}
					}
				}else if(sound.soundName.endsWith("_supercharger")){
					if(!state.running && internalFuel == 0){
						sound.stop();
					}else{
						sound.volume = (float) rpm/definition.engine.maxRPM;
						if(definition.engine.isSteamPowered){
							sound.pitch = 1.0F;
						}else{
							sound.pitch = (float) (0.35*(1 + Math.max(0, (rpm - startRPM))/(definition.engine.maxRPM < 15000 ? 500 : 5000)));
						}
					}
				}else if(sound.soundName.endsWith("_piston")){
					sound.pitch = (float) (0.35*(1 + Math.max(0, (rpm - startRPM))/500D)); 
				}
			}
		}
	}
	
	@Override
	public void restartSound(SoundInstance sound){
		if(definition.engine.customSoundset != null){
			for(EngineSound soundDefinition : definition.engine.customSoundset){
				if(sound.soundName.equals(soundDefinition.soundName)){
					InterfaceAudio.playQuickSound(new SoundInstance(this, sound.soundName, true));
				}
			}
		}else if(sound.soundName.endsWith("_cranking") || sound.soundName.endsWith("_running") || sound.soundName.endsWith("_supercharger")){
			InterfaceAudio.playQuickSound(new SoundInstance(this, sound.soundName, true));
		}else if(sound.soundName.endsWith("backup_beeper")){
			InterfaceAudio.playQuickSound(new SoundInstance(this, MTS.MODID + ":backup_beeper", true));
		}
	}

	
	
	//--------------------START OF ENGINE PARTICLE METHODS--------------------
	
	@Override
	public void spawnParticles(){
		//Render exhaust smoke if we have any exhausts and are running.
		//If we are starting and have flames set, render those instead.
		if(vehicleDefinition.exhaustPos != null && (state.running || (definition.engine.flamesOnStartup && state.esOn))){
			//Render a smoke for every cycle the exhaust makes.
			//Depending on the number of positions we have, render an exhaust for every one.
			//So for 1 position, we render 1 every 2 engine cycles (4 stroke), and for 4, we render 4.
			//Note that the rendering is offset for multi-position points to simulate the cylinders firing
			//in their aligned order.
			
			//Get timing information and particle information.
			//Need to check for 0 cycle time if RPM is somehow 0 here.
			long engineCycleTimeMills = (long) (2D*(1D/(rpm/60D/1000D)));
			long currentTime = System.currentTimeMillis();
			if(engineCycleTimeMills != 0){
				long camTime = currentTime%engineCycleTimeMills;
				
				float particleColor = definition.engine.isSteamPowered ? 0.0F : (float) Math.max(1 - temp/COLD_TEMP, 0);
				boolean singleExhaust = vehicleDefinition.exhaustPos.length == 3;
				
				//Iterate through all the exhaust positions and fire them if it is time to do so.
				//We need to offset the time we are supposed to spawn by the cycle time for multi-point exhausts.
				//For single-point exhausts, we only fire if we didn't fire this cycle.
				for(int i=0; i<vehicleDefinition.exhaustPos.length; i+=3){
					if(singleExhaust){
						if(lastTimeParticleSpawned + camTime > currentTime){
							continue;
						}
					}else{
						long camOffset = engineCycleTimeMills*3/vehicleDefinition.exhaustPos.length;
						long camMin = (i/3)*camOffset;
						long camMax = camMin + camOffset;
						if(camTime < camMin || camTime > camMax || (lastTimeParticleSpawned > camMin && lastTimeParticleSpawned < camMax)){
							continue;
						}
					}
					
					Point3d exhaustOffset = new Point3d(vehicleDefinition.exhaustPos[i], vehicleDefinition.exhaustPos[i+1], vehicleDefinition.exhaustPos[i+2]).rotateFine(vehicle.angles).add(vehicle.position);
					Point3d velocityOffset = new Point3d(vehicleDefinition.exhaustVelocity[i], vehicleDefinition.exhaustVelocity[i+1], vehicleDefinition.exhaustVelocity[i+2]).rotateFine(vehicle.angles);
					velocityOffset.x = velocityOffset.x/10D + 0.02 - Math.random()*0.04;
					velocityOffset.y = velocityOffset.y/10D;
					velocityOffset.z = velocityOffset.z/10D + 0.02 - Math.random()*0.14;
					if(state.running){
						InterfaceRender.spawnParticle(new ParticleSmoke(vehicle.world, exhaustOffset, velocityOffset, particleColor, particleColor, particleColor, (float) Math.min((50 + hours)/500, 1), 1.0F));
						//Also play steam chuff sound if we are a steam engine.
						if(definition.engine.isSteamPowered){
							InterfaceAudio.playQuickSound(new SoundInstance(this, definition.packID + ":" + definition.systemName + "_piston"));
						}
					}
					if(definition.engine.flamesOnStartup && state.esOn){
						InterfaceRender.spawnParticle(new ParticleFlame(vehicle.world, exhaustOffset, velocityOffset, 1.0F));
					}
					lastTimeParticleSpawned = singleExhaust ? currentTime : camTime;
				}
			}
		}
		
		//If we backfired, render a few puffs.
		//Will be from the engine or the exhaust if we have any.
		if(backfired){
			backfired = false;
			if(vehicleDefinition.exhaustPos != null){
				for(int i=0; i<vehicleDefinition.exhaustPos.length; i+=3){
					Point3d exhaustOffset = new Point3d(vehicleDefinition.exhaustPos[i], vehicleDefinition.exhaustPos[i+1], vehicleDefinition.exhaustPos[i+2]).rotateFine(vehicle.angles).add(vehicle.position);
					Point3d velocityOffset = new Point3d(vehicleDefinition.exhaustVelocity[i], vehicleDefinition.exhaustVelocity[i+1], vehicleDefinition.exhaustVelocity[i+2]).rotateFine(vehicle.angles);
					velocityOffset.x = velocityOffset.x/10D + 0.07 - Math.random()*0.14;
					velocityOffset.y = velocityOffset.y/10D;
					velocityOffset.z = velocityOffset.z/10D + 0.07 - Math.random()*0.14;
					for(byte j=0; j<5; ++j){
						InterfaceRender.spawnParticle(new ParticleSmoke(vehicle.world, exhaustOffset, velocityOffset, 0.0F, 0.0F, 0.0F, 1.0F, 2.5F));
					}
				}
			}else{
				for(byte i=0; i<5; ++i){
					InterfaceRender.spawnParticle(new ParticleSmoke(vehicle.world, worldPos, new Point3d(0.07 - Math.random()*0.14, 0.15, 0.07 - Math.random()*0.14), 0.0F, 0.0F, 0.0F, 1.0F, 2.5F));
				}
			}
		}
		
		//Render oil and fuel leak particles.
		if(oilLeak){
			if(vehicle.ticksExisted%20 == 0){
				InterfaceRender.spawnParticle(new ParticleDrip(vehicle.world, worldPos, vehicle.motion, 0.0F, 0.0F, 0.0F, 1.0F));
			}
		}
		if(fuelLeak){
			if((vehicle.ticksExisted + 5)%20 == 0){
				InterfaceRender.spawnParticle(new ParticleDrip(vehicle.world, worldPos, vehicle.motion, 1.0F, 0.0F, 0.0F, 1.0F));
			}
		}
		
		//Render engine smoke if we're overheating.  Only for non-steam engines.
		if(!definition.engine.isSteamPowered && temp > OVERHEAT_TEMP_1){
			InterfaceRender.spawnParticle(new ParticleSmoke(vehicle.world, worldPos, new Point3d(0, 0.15, 0), 0.0F, 0.0F, 0.0F, 1.0F, 1.0F));
			if(temp > OVERHEAT_TEMP_2){
				InterfaceRender.spawnParticle(new ParticleSmoke(vehicle.world, worldPos, new Point3d(0, 0.15, 0), 0.0F, 0.0F, 0.0F, 1.0F, 2.5F));
			}
		}
	}
	
	public enum EngineStates{
		ENGINE_OFF(false, false, false, false),
		MAGNETO_ON_STARTERS_OFF(true, false, false, false),
		MAGNETO_OFF_ES_ON(false, true, false, false),
		MAGNETO_OFF_HS_ON(false, false, true, false),
		MAGNETO_ON_ES_ON(true, true, false, false),
		MAGNETO_ON_HS_ON(true, false, true, false),
		RUNNING(true, false, false, true),
		RUNNING_ES_ON(true, true, false, true),
		RUNNING_HS_ON(true, false, true, true);
		
		public final boolean magnetoOn;
		public final boolean esOn;
		public final boolean hsOn;
		public final boolean running;
		
		private EngineStates(boolean magnetoOn, boolean esOn, boolean hsOn, boolean running){
			this.magnetoOn = magnetoOn;
			this.esOn = esOn;
			this.hsOn = hsOn;
			this.running = running;
		}
	}
}
