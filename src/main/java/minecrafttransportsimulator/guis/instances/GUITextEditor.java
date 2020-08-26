package minecrafttransportsimulator.guis.instances;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import mcinterface.BuilderGUI;
import mcinterface.BuilderGUI.TextRendering;
import mcinterface.InterfaceNetwork;
import minecrafttransportsimulator.blocks.components.ABlockBase.Axis;
import minecrafttransportsimulator.blocks.tileentities.instances.TileEntityPole;
import minecrafttransportsimulator.blocks.tileentities.instances.TileEntityPole_Sign;
import minecrafttransportsimulator.guis.components.AGUIBase;
import minecrafttransportsimulator.guis.components.GUIComponentButton;
import minecrafttransportsimulator.guis.components.GUIComponentLabel;
import minecrafttransportsimulator.guis.components.GUIComponentOBJModel;
import minecrafttransportsimulator.guis.components.GUIComponentTextBox;
import minecrafttransportsimulator.jsondefs.JSONText;
import minecrafttransportsimulator.packets.instances.PacketTileEntityPoleChange;
import minecrafttransportsimulator.packets.instances.PacketVehicleTextChange;
import minecrafttransportsimulator.vehicles.main.EntityVehicleF_Physics;
import minecrafttransportsimulator.vehicles.parts.APart;

public class GUITextEditor extends AGUIBase{
	//Buttons.
	private GUIComponentButton confirmButton;
	
	//Input boxes
	private final List<GUIComponentTextBox> textBoxes = new ArrayList<GUIComponentTextBox>();
	private final List<GUIComponentLabel> signLabels = new ArrayList<GUIComponentLabel>();
	
	//Pole and axis clicked on pole.
	private final TileEntityPole pole;
	private final Axis axis;
	
	//Clicked vehicle.
	private final EntityVehicleF_Physics vehicle;
	
	//List of what vehicle text lines go to what entry. 
	private final List<Integer> vehicleTextBoxes = new ArrayList<Integer>();
	
	public GUITextEditor(TileEntityPole pole, Axis axis){
		this.pole = pole;
		this.axis = axis;
		this.vehicle = null;
	}
	
	public GUITextEditor(EntityVehicleF_Physics vehicle){
		this.pole = null;
		this.axis = null;
		this.vehicle = vehicle;
	}
	
	@Override 
	public void setupComponents(int guiLeft, int guiTop){
		List<JSONText> textObjects;
		List<String> textLines;
		textBoxes.clear();
		if(pole != null){
			TileEntityPole_Sign sign = (TileEntityPole_Sign) pole.components.get(axis);
			
			//Add the render to render the sign.
			GUIComponentOBJModel modelRender = new GUIComponentOBJModel(guiLeft + 200, guiTop + 100, 64.0F, false, false, false);
			addOBJModel(modelRender);
			modelRender.modelDomain = sign.definition.packID;
			if(sign.definition.general.modelName != null){
				modelRender.modelLocation = "objmodels/poles/" + sign.definition.general.modelName + ".obj";
			}else{
				modelRender.modelLocation = "objmodels/poles/" + sign.definition.systemName + ".obj";
			}
			modelRender.textureDomain = sign.definition.packID;
			modelRender.textureLocation = "textures/poles/" + sign.definition.systemName + ".png";
			
			//Set text and text objects.
			textObjects = pole.components.get(axis).definition.general.textObjects;
			textLines = sign.getTextLines();
			
			//Add render-able labels for the sign object.
			for(byte i=0; i<textObjects.size(); ++i){
				JSONText textObject = textObjects.get(i);
				GUIComponentLabel label = new GUIComponentLabel(modelRender.x + (int) (textObject.pos[0]*64F), modelRender.y - (int) (textObject.pos[1]*64F), Color.decode(textObject.color), sign.getTextLines().get(i), textObject.scale*64F/16F, TextRendering.values()[textObject.renderMode], false, textObject.wrapWidth);
				addLabel(label);
				signLabels.add(label);
			}
		}else{
			//Set text and text objects.
			textObjects = new ArrayList<JSONText>();
			textLines = new ArrayList<String>();
			if(vehicle.definition.rendering.textObjects != null){
				for(JSONText textObject : vehicle.definition.rendering.textObjects){
					textObjects.add(textObject);
					textLines.add(vehicle.textLines.get(textObjects.indexOf(textObject)));
				}
			}
			for(APart part : vehicle.parts){
				if(part.definition.rendering != null && part.definition.rendering.textObjects != null){
					for(JSONText textObject : part.definition.rendering.textObjects){
						textObjects.add(textObject);
						textLines.add(part.textLines.get(textObjects.indexOf(textObject)));
					}
				}
			}
		}
		
		//Add text box components for every text.  Paired with labels to render the text name above the boxes.
		//Don't add multiple boxes per text field, however.  Those use the same box.
		List<String> renderedFields = new ArrayList<String>();
		vehicleTextBoxes.clear();
		int currentOffset = 0;
		int boxWidth = vehicle != null ? 200 : 100;
		for(JSONText textObject : textObjects){
			if(!renderedFields.contains(textObject.fieldName)){
				GUIComponentLabel label = new GUIComponentLabel(guiLeft + 20, guiTop + 20 + currentOffset, Color.BLACK, textObject.fieldName);
				addLabel(label);
				int textRowsRequired = 1 + 5*textObject.maxLength/boxWidth;
				GUIComponentTextBox box = new GUIComponentTextBox(guiLeft + 20, label.y + 10, boxWidth, textLines.get(textObjects.indexOf(textObject)), 12*textRowsRequired, Color.WHITE, Color.BLACK, textObject.maxLength);
				addTextBox(box);
				vehicleTextBoxes.add(textBoxes.size());
				renderedFields.add(textObject.fieldName);
				textBoxes.add(box);
				currentOffset += box.height + 12;
			}else{
				vehicleTextBoxes.add(renderedFields.indexOf(textObject.fieldName));
			}
		}
		
		//Add the confirm button.
		addButton(confirmButton = new GUIComponentButton(guiLeft + 150, guiTop + 160, 80, BuilderGUI.translate("gui.trafficsignalcontroller.confirm")){
			@Override
			public void onClicked(){
				if(pole != null){
					//Copy text from boxes to string list.
					List<String> textLines = new ArrayList<String>();
					for(GUIComponentTextBox box : textBoxes){
						textLines.add(box.getText());
					}
					InterfaceNetwork.sendToServer(new PacketTileEntityPoleChange(pole, axis, null, textLines, false));
				}else{
					//Copy text from boxes to string list.
					List<String> textLines = new ArrayList<String>();
					for(Integer textBoxIndex : vehicleTextBoxes){
						textLines.add(textBoxes.get(textBoxIndex).getText());
					}
					InterfaceNetwork.sendToServer(new PacketVehicleTextChange(vehicle, textLines));
				}
				BuilderGUI.closeGUI();
			}
		});
	}
	
	@Override
	public void setStates(){
		confirmButton.enabled = true;
		for(byte i=0; i<signLabels.size(); ++i){
			signLabels.get(i).text = textBoxes.get(i).getText();
		}
	}
}
