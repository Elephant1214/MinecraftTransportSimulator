package minecrafttransportsimulator.guis.instances;

import minecrafttransportsimulator.baseclasses.ColorRGB;
import minecrafttransportsimulator.baseclasses.Point3D;
import minecrafttransportsimulator.blocks.components.ABlockBase.Axis;
import minecrafttransportsimulator.blocks.tileentities.components.ATileEntityBase;
import minecrafttransportsimulator.blocks.tileentities.components.ATileEntityPole_Component;
import minecrafttransportsimulator.blocks.tileentities.instances.TileEntityPole;
import minecrafttransportsimulator.blocks.tileentities.instances.TileEntityPole_TrafficSignal;
import minecrafttransportsimulator.blocks.tileentities.instances.TileEntitySignalController;
import minecrafttransportsimulator.blocks.tileentities.instances.TileEntitySignalController.IntersectionProperties;
import minecrafttransportsimulator.guis.components.AGUIBase;
import minecrafttransportsimulator.guis.components.GUIComponentButton;
import minecrafttransportsimulator.guis.components.GUIComponentLabel;
import minecrafttransportsimulator.guis.components.GUIComponentTextBox;
import minecrafttransportsimulator.mcinterface.InterfaceManager;
import minecrafttransportsimulator.packets.instances.PacketTileEntitySignalControllerChange;
import minecrafttransportsimulator.rendering.RenderText.TextAlignment;
import minecrafttransportsimulator.systems.LanguageSystem;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GUISignalController extends AGUIBase {

    //Intersection property boxes.
    private final Set<GUIComponentIntersectionProperties> intersectionPropertyComponents = new HashSet<>();
    private final List<GUIComponentLabel> upperPropertyLabels = new ArrayList<>();
    private final List<GUIComponentLabel> lowerPropertyLabels = new ArrayList<>();
    //Controller we're linked to.
    private final TileEntitySignalController controller;
    //Buttons.
    private GUIComponentButton scanButton;
    private GUIComponentButton directionButton;
    private GUIComponentButton cycleButton;
    private GUIComponentButton driveSideButton;
    private GUIComponentButton stopYellowButton;
    private boolean onLaneScreen;
    //Label for scan results.
    private GUIComponentLabel trafficSignalCount;
    //Input boxes
    private GUIComponentNumericTextBox scanDistanceText;
    private GUIComponentNumericTextBox scanCenterXText;
    private GUIComponentNumericTextBox scanCenterZText;
    private GUIComponentNumericTextBox laneWidthText;
    private GUIComponentNumericTextBox greenMainTimeText;
    private GUIComponentNumericTextBox greenCrossTimeText;
    private GUIComponentNumericTextBox yellowMainTimeText;
    private GUIComponentNumericTextBox yellowCrossTimeText;
    private GUIComponentNumericTextBox allRedTimeText;

    public GUISignalController(TileEntitySignalController controller) {
        super();
        this.controller = controller;
    }

    @Override
    public void setupComponents() {
        super.setupComponents();
        int topOffset = guiTop + 15;
        int leftTextOffset = guiLeft + 20;
        int leftObjectOffset = guiLeft + 100;
        int middleObjectOffset = guiLeft + 140;
        int rowSpacing = 2;

        //Main scan button.
        addComponent(scanButton = new GUIComponentButton(this, leftTextOffset, topOffset, 220, 15, LanguageSystem.GUI_SIGNALCONTROLLER_SCAN.getCurrentValue()) {
            @Override
            public void onClicked(boolean leftSide) {
                controller.componentLocations.clear();
                int scanDistance = Integer.parseInt(scanDistanceText.getText());
                double minX = Double.MAX_VALUE;
                double maxX = -Double.MAX_VALUE;
                double minZ = Double.MAX_VALUE;
                double maxZ = -Double.MAX_VALUE;
                for (double i = controller.position.x - scanDistance; i <= controller.position.x + scanDistance; ++i) {
                    for (double j = controller.position.y - scanDistance; j <= controller.position.y + scanDistance; ++j) {
                        for (double k = controller.position.z - scanDistance; k <= controller.position.z + scanDistance; ++k) {
                            Point3D checkPosition = new Point3D(i, j, k);
                            ATileEntityBase<?> tile = controller.world.getTileEntity(checkPosition);
                            if (tile instanceof TileEntityPole) {
                                for (ATileEntityPole_Component component : ((TileEntityPole) tile).components.values()) {
                                    if (component instanceof TileEntityPole_TrafficSignal) {
                                        controller.componentLocations.add(tile.position);
                                        minX = Math.min(minX, tile.position.x);
                                        maxX = Math.max(maxX, tile.position.x);
                                        minZ = Math.min(minZ, tile.position.z);
                                        maxZ = Math.max(maxZ, tile.position.z);
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
                //Offset intersection center by 0.5 to account for the fact we're checking block bounds and blocks are entered.
                controller.intersectionCenterPoint.set(minX + (maxX - minX) / 2, controller.position.y, minZ + (maxZ - minZ) / 2);
                scanCenterXText.setText(String.valueOf(controller.intersectionCenterPoint.x));
                scanCenterZText.setText(String.valueOf(controller.intersectionCenterPoint.z));
                double averageDistance = ((maxX - minX) / 2D + (maxZ - minZ) / 2D) / 2D;
                for (Axis axis : controller.intersectionProperties.keySet()) {
                    IntersectionProperties properties = controller.intersectionProperties.get(axis);
                    properties.roadWidth = averageDistance;
                    properties.centerLaneCount = (int) Math.max(1, properties.roadWidth / laneWidthText.getDoubleValue());
                    properties.centerDistance = averageDistance;
                    if (controller.isRightHandDrive) {
                        properties.centerOffset = -averageDistance;
                    }
                    for (GUIComponentIntersectionProperties component : intersectionPropertyComponents) {
                        if (component.axis.equals(axis)) {
                            component.centerLaneText.setText(String.valueOf(properties.centerLaneCount));
                            component.roadWidthText.setText(String.valueOf(properties.roadWidth));
                            component.centerDistanceText.setText(String.valueOf(properties.centerDistance));
                            component.centerOffsetText.setText(String.valueOf(properties.centerOffset));
                            break;
                        }
                    }
                }
                controller.initializeController(null);
                controller.unsavedClientChangesPreset = true;
            }
        });
        topOffset += scanButton.height + rowSpacing;

        //Scan center.
        addComponent(scanCenterXText = new GUIComponentNumericTextBox(this, leftObjectOffset, topOffset, String.valueOf(controller.intersectionCenterPoint.x), 60) {
            @Override
            public void setVariable() {
                controller.intersectionCenterPoint.x = getDoubleValue();
            }
        });
        addComponent(scanCenterZText = new GUIComponentNumericTextBox(this, scanCenterXText.constructedX + scanCenterXText.width + 5, topOffset, String.valueOf(controller.intersectionCenterPoint.z), 60) {
            @Override
            public void setVariable() {
                controller.intersectionCenterPoint.z = getDoubleValue();
            }
        });
        addComponent(new GUIComponentLabel(leftTextOffset, topOffset, ColorRGB.WHITE, LanguageSystem.GUI_SIGNALCONTROLLER_SCANCENTER.getCurrentValue()).setComponent(scanCenterXText));
        topOffset += scanCenterXText.height + rowSpacing;

        //Scan distance.
        addComponent(scanDistanceText = new GUIComponentNumericTextBox(this, leftObjectOffset, topOffset, "25"));
        addComponent(new GUIComponentLabel(leftTextOffset, topOffset, ColorRGB.WHITE, LanguageSystem.GUI_SIGNALCONTROLLER_SCANDISTANCE.getCurrentValue()).setComponent(scanDistanceText));

        //Found count.
        addComponent(trafficSignalCount = new GUIComponentLabel(scanDistanceText.constructedX + scanDistanceText.width + 5, topOffset, ColorRGB.WHITE, LanguageSystem.GUI_SIGNALCONTROLLER_SCANFOUND.getCurrentValue() + controller.componentLocations.size()));
        topOffset += scanDistanceText.height + rowSpacing * 3;

        //Stop yellow switch.
        addComponent(stopYellowButton = new GUIComponentButton(this, leftTextOffset, topOffset, 115, 15, controller.hasStopYellow ? LanguageSystem.GUI_SIGNALCONTROLLER_STOPYELLOW.getCurrentValue() : LanguageSystem.GUI_SIGNALCONTROLLER_STOPONLY.getCurrentValue()) {
            @Override
            public void onClicked(boolean leftSide) {
                controller.hasStopYellow = !controller.hasStopYellow;
                this.text = controller.hasStopYellow ? LanguageSystem.GUI_SIGNALCONTROLLER_STOPYELLOW.getCurrentValue() : LanguageSystem.GUI_SIGNALCONTROLLER_STOPONLY.getCurrentValue();
                controller.unsavedClientChangesPreset = true;
                controller.initializeController(null);
            }
        });

        //Timed mode direction.
        addComponent(cycleButton = new GUIComponentButton(this, middleObjectOffset, topOffset, 100, 15, controller.timedMode ? LanguageSystem.GUI_SIGNALCONTROLLER_TIMEMODE.getCurrentValue() : LanguageSystem.GUI_SIGNALCONTROLLER_TRIGGERMODE.getCurrentValue()) {
            @Override
            public void onClicked(boolean leftSide) {
                controller.timedMode = !controller.timedMode;
                this.text = controller.timedMode ? LanguageSystem.GUI_SIGNALCONTROLLER_TIMEMODE.getCurrentValue() : LanguageSystem.GUI_SIGNALCONTROLLER_TRIGGERMODE.getCurrentValue();
                controller.unsavedClientChangesPreset = true;
                controller.initializeController(null);
            }
        });
        topOffset += cycleButton.height + rowSpacing;

        //Primary direction.
        addComponent(directionButton = new GUIComponentButton(this, leftTextOffset, topOffset, 115, 15, LanguageSystem.GUI_SIGNALCONTROLLER_PRIMARYAXIS.getCurrentValue() + controller.mainDirectionAxis.name()) {
            @Override
            public void onClicked(boolean leftSide) {
                switch (controller.mainDirectionAxis) {
                    case NORTH:
                        controller.mainDirectionAxis = Axis.EAST;
                        break;
                    case EAST:
                        controller.mainDirectionAxis = Axis.NORTHEAST;
                        break;
                    case NORTHEAST:
                        controller.mainDirectionAxis = Axis.NORTHWEST;
                        break;
                    default:
                        controller.mainDirectionAxis = Axis.NORTH;
                        break;
                }
                this.text = LanguageSystem.GUI_SIGNALCONTROLLER_PRIMARYAXIS.getCurrentValue() + controller.mainDirectionAxis.name();
                controller.unsavedClientChangesPreset = true;
                controller.initializeController(null);
            }
        });

        //RHD/LHD switch.
        addComponent(driveSideButton = new GUIComponentButton(this, middleObjectOffset, topOffset, 100, 15, controller.isRightHandDrive ? LanguageSystem.GUI_SIGNALCONTROLLER_RIGHTHANDDRIVE.getCurrentValue() : LanguageSystem.GUI_SIGNALCONTROLLER_LEFTHANDDRIVE.getCurrentValue()) {
            @Override
            public void onClicked(boolean leftSide) {
                controller.isRightHandDrive = !controller.isRightHandDrive;
                this.text = controller.isRightHandDrive ? LanguageSystem.GUI_SIGNALCONTROLLER_RIGHTHANDDRIVE.getCurrentValue() : LanguageSystem.GUI_SIGNALCONTROLLER_LEFTHANDDRIVE.getCurrentValue();
                controller.unsavedClientChangesPreset = true;
                controller.initializeController(null);
            }
        });

        topOffset += 15 + rowSpacing * 3;

        //Lane width defaults.
        addComponent(laneWidthText = new GUIComponentNumericTextBox(this, middleObjectOffset, topOffset, "4.0"));
        addComponent(new GUIComponentLabel(leftTextOffset, topOffset, ColorRGB.WHITE, LanguageSystem.GUI_SIGNALCONTROLLER_LANEWIDTH.getCurrentValue()).setComponent(laneWidthText));
        topOffset += GUIComponentNumericTextBox.NUMERIC_HEIGHT;

        //Time text.  These auto-forward their values.
        addComponent(greenMainTimeText = new GUIComponentNumericTextBox(this, middleObjectOffset, topOffset, String.valueOf(controller.greenMainTime / 20)) {
            @Override
            public void setVariable() {
                controller.greenMainTime = getIntegerValue() * 20;
            }
        });
        addComponent(new GUIComponentLabel(leftTextOffset, topOffset, ColorRGB.WHITE, LanguageSystem.GUI_SIGNALCONTROLLER_GREENMAINTIME.getCurrentValue()).setComponent(greenMainTimeText));
        topOffset += GUIComponentNumericTextBox.NUMERIC_HEIGHT;

        addComponent(greenCrossTimeText = new GUIComponentNumericTextBox(this, middleObjectOffset, topOffset, String.valueOf(controller.greenCrossTime / 20)) {
            @Override
            public void setVariable() {
                controller.greenCrossTime = getIntegerValue() * 20;
            }
        });
        addComponent(new GUIComponentLabel(leftTextOffset, topOffset, ColorRGB.WHITE, LanguageSystem.GUI_SIGNALCONTROLLER_GREENCROSSTIME.getCurrentValue()).setComponent(greenCrossTimeText));
        topOffset += GUIComponentNumericTextBox.NUMERIC_HEIGHT;

        addComponent(yellowMainTimeText = new GUIComponentNumericTextBox(this, middleObjectOffset, topOffset, String.valueOf(controller.yellowMainTime / 20)) {
            @Override
            public void setVariable() {
                controller.yellowMainTime = getIntegerValue() * 20;
            }
        });
        addComponent(new GUIComponentLabel(leftTextOffset, topOffset, ColorRGB.WHITE, LanguageSystem.GUI_SIGNALCONTROLLER_YELLOWMAINTIME.getCurrentValue()).setComponent(yellowMainTimeText));
        topOffset += GUIComponentNumericTextBox.NUMERIC_HEIGHT;

        addComponent(yellowCrossTimeText = new GUIComponentNumericTextBox(this, middleObjectOffset, topOffset, String.valueOf(controller.yellowCrossTime / 20)) {
            @Override
            public void setVariable() {
                controller.yellowCrossTime = getIntegerValue() * 20;
            }
        });
        addComponent(new GUIComponentLabel(leftTextOffset, topOffset, ColorRGB.WHITE, LanguageSystem.GUI_SIGNALCONTROLLER_YELLOWCROSSTIME.getCurrentValue()).setComponent(yellowCrossTimeText));
        topOffset += GUIComponentNumericTextBox.NUMERIC_HEIGHT;

        addComponent(allRedTimeText = new GUIComponentNumericTextBox(this, middleObjectOffset, topOffset, String.valueOf(controller.allRedTime / 20)) {
            @Override
            public void setVariable() {
                controller.allRedTime = getIntegerValue() * 20;
            }
        });
        addComponent(new GUIComponentLabel(leftTextOffset, topOffset, ColorRGB.WHITE, LanguageSystem.GUI_SIGNALCONTROLLER_ALLREDTIME.getCurrentValue()).setComponent(allRedTimeText));
        topOffset += GUIComponentNumericTextBox.NUMERIC_HEIGHT + rowSpacing * 4;

        //Change screen button.
        addComponent(new GUIComponentButton(this, leftTextOffset, topOffset, 100, 15, onLaneScreen ? LanguageSystem.GUI_SIGNALCONTROLLER_SIGNALSETTINGS.getCurrentValue() : LanguageSystem.GUI_SIGNALCONTROLLER_LANESETTINGS.getCurrentValue()) {
            @Override
            public void onClicked(boolean leftSide) {
                onLaneScreen = !onLaneScreen;
                this.text = onLaneScreen ? LanguageSystem.GUI_SIGNALCONTROLLER_SIGNALSETTINGS.getCurrentValue() : LanguageSystem.GUI_SIGNALCONTROLLER_LANESETTINGS.getCurrentValue();
            }
        });

        //Confirm button.
        addComponent(new GUIComponentButton(this, guiLeft + getWidth() - 100, topOffset, 80, 15, LanguageSystem.GUI_CONFIRM.getCurrentValue()) {
            @Override
            public void onClicked(boolean leftSide) {
                InterfaceManager.packetInterface.sendToServer(new PacketTileEntitySignalControllerChange(controller));
                controller.unsavedClientChangesPreset = false;
                close();
            }
        });

        //Properties.
        int baseLeftOffset = 80;
        int incrementalLeftOffset = 40;
        leftTextOffset = guiLeft + baseLeftOffset;
        topOffset = guiTop + 10;
        intersectionPropertyComponents.clear();
        upperPropertyLabels.clear();
        lowerPropertyLabels.clear();
        for (Axis axis : Axis.values()) {
            if (axis.xzPlanar) {
                GUIComponentIntersectionProperties propertiesComponent = new GUIComponentIntersectionProperties(this, guiLeft, guiTop, leftTextOffset, topOffset, axis);
                intersectionPropertyComponents.add(propertiesComponent);
                leftTextOffset += incrementalLeftOffset;
                if (leftTextOffset >= guiLeft + baseLeftOffset + 4 * incrementalLeftOffset) {
                    leftTextOffset = guiLeft + baseLeftOffset;
                    topOffset += 75;
                }

                List<GUIComponentLabel> currentList = axis.blockBased ? upperPropertyLabels : lowerPropertyLabels;
                if (currentList.isEmpty()) {
                    currentList.add(new GUIComponentLabel(guiLeft + 10, topOffset + 10, ColorRGB.WHITE, LanguageSystem.GUI_SIGNALCONTROLLER_LEFTLANES.getCurrentValue(), TextAlignment.LEFT_ALIGNED, 0.75F));
                    currentList.add(new GUIComponentLabel(guiLeft + 10, topOffset + 20, ColorRGB.WHITE, LanguageSystem.GUI_SIGNALCONTROLLER_CENTERLANES.getCurrentValue(), TextAlignment.LEFT_ALIGNED, 0.75F));
                    currentList.add(new GUIComponentLabel(guiLeft + 10, topOffset + 30, ColorRGB.WHITE, LanguageSystem.GUI_SIGNALCONTROLLER_RIGHTLANES.getCurrentValue(), TextAlignment.LEFT_ALIGNED, 0.75F));
                    currentList.add(new GUIComponentLabel(guiLeft + 10, topOffset + 40, ColorRGB.WHITE, LanguageSystem.GUI_SIGNALCONTROLLER_ROADWIDTH.getCurrentValue(), TextAlignment.LEFT_ALIGNED, 0.75F));
                    currentList.add(new GUIComponentLabel(guiLeft + 10, topOffset + 50, ColorRGB.WHITE, LanguageSystem.GUI_SIGNALCONTROLLER_CENTERDIST.getCurrentValue(), TextAlignment.LEFT_ALIGNED, 0.75F));
                    currentList.add(new GUIComponentLabel(guiLeft + 10, topOffset + 60, ColorRGB.WHITE, LanguageSystem.GUI_SIGNALCONTROLLER_MEDIANDIST.getCurrentValue(), TextAlignment.LEFT_ALIGNED, 0.75F));
                    for (GUIComponentLabel label : currentList) {
                        addComponent(label);
                    }
                }
            }
        }
    }

    @Override
    public void setStates() {
        super.setStates();
        trafficSignalCount.text = LanguageSystem.GUI_SIGNALCONTROLLER_SCANFOUND.getCurrentValue() + controller.componentLocations.size();

        scanButton.visible = !onLaneScreen;
        directionButton.visible = !onLaneScreen;
        cycleButton.visible = !onLaneScreen;
        driveSideButton.visible = !onLaneScreen;
        stopYellowButton.visible = !onLaneScreen;

        scanCenterXText.visible = !onLaneScreen;
        scanCenterZText.visible = !onLaneScreen;
        scanDistanceText.visible = !onLaneScreen;
        trafficSignalCount.visible = !onLaneScreen;

        laneWidthText.visible = !onLaneScreen;
        greenMainTimeText.visible = !onLaneScreen;
        greenCrossTimeText.visible = !onLaneScreen;
        yellowMainTimeText.visible = !onLaneScreen;
        yellowCrossTimeText.visible = !onLaneScreen;
        allRedTimeText.visible = !onLaneScreen;

        boolean upperLabelsVisible = false;
        boolean lowerLabelsVisible = false;
        for (GUIComponentIntersectionProperties propertyComponent : intersectionPropertyComponents) {
            boolean showGroup = onLaneScreen && controller.intersectionProperties.get(propertyComponent.axis).isActive;
            propertyComponent.axisLabel.visible = showGroup;
            propertyComponent.leftLaneText.visible = showGroup;
            propertyComponent.centerLaneText.visible = showGroup;
            propertyComponent.rightLaneText.visible = showGroup;
            propertyComponent.roadWidthText.visible = showGroup;
            propertyComponent.centerDistanceText.visible = showGroup;
            propertyComponent.centerOffsetText.visible = showGroup;
            if (showGroup) {
                if (propertyComponent.axis.blockBased) {
                    upperLabelsVisible = true;
                } else {
                    lowerLabelsVisible = true;
                }
            }
        }

        for (GUIComponentLabel label : upperPropertyLabels) {
            label.visible = upperLabelsVisible;
        }
        for (GUIComponentLabel label : lowerPropertyLabels) {
            label.visible = lowerLabelsVisible;
        }
    }

    @Override
    protected boolean canStayOpen() {
        return super.canStayOpen() && controller.isValid;
    }

    private class GUIComponentNumericTextBox extends GUIComponentTextBox {
        private static final int NUMERIC_HEIGHT = 10;
        private final boolean floatingPoint;

        public GUIComponentNumericTextBox(AGUIBase gui, int x, int y, String text) {
            super(gui, x, y, 40, NUMERIC_HEIGHT, text, ColorRGB.WHITE, 5);
            this.floatingPoint = false;
        }

        public GUIComponentNumericTextBox(AGUIBase gui, int x, int y, String text, int width) {
            super(gui, x, y, width, NUMERIC_HEIGHT, text, ColorRGB.WHITE, 7);
            this.floatingPoint = true;
        }

        @Override
        public void handleTextChange() {
            controller.unsavedClientChangesPreset = true;
            setVariable();
            controller.initializeController(null);
        }

        @Override
        public boolean isTextValid(String newText) {
            //Only allow numbers.
            if (newText.isEmpty()) {
                return true;
            } else {
                if (floatingPoint) {
                    return newText.matches("-?\\d+(\\.\\d+)?");
                } else {
                    return newText.matches("\\d+");
                }
            }
        }

        protected void setVariable() {
        }

        protected int getIntegerValue() {
            return text.isEmpty() ? 0 : Integer.parseInt(text);
        }

        protected double getDoubleValue() {
            return text.isEmpty() ? 0 : Double.parseDouble(text);
        }
    }

    private class GUIComponentIntersectionProperties {
        private final Axis axis;
        private final GUIComponentLabel axisLabel;
        private final GUIComponentTextBox leftLaneText;
        private final GUIComponentTextBox centerLaneText;
        private final GUIComponentTextBox rightLaneText;
        private final GUIComponentTextBox roadWidthText;
        private final GUIComponentTextBox centerDistanceText;
        private final GUIComponentTextBox centerOffsetText;

        private GUIComponentIntersectionProperties(AGUIBase gui, int guiLeft, int guiTop, int leftOffset, int topOffset, Axis axis) {
            this.axis = axis;
            IntersectionProperties properties = controller.intersectionProperties.get(axis);
            addComponent(axisLabel = new GUIComponentLabel(leftOffset, topOffset, ColorRGB.WHITE, axis.name(), TextAlignment.LEFT_ALIGNED, axis.blockBased ? 1.0F : 0.65F));
            addComponent(leftLaneText = new GUIComponentNumericTextBox(gui, leftOffset, topOffset + 10, String.valueOf(properties.leftLaneCount)) {
                @Override
                public void setVariable() {
                    controller.intersectionProperties.get(axis).leftLaneCount = getIntegerValue();
                }
            });

            addComponent(centerLaneText = new GUIComponentNumericTextBox(gui, leftOffset, topOffset + 20, String.valueOf(properties.centerLaneCount)) {
                @Override
                public void setVariable() {
                    controller.intersectionProperties.get(axis).centerLaneCount = getIntegerValue();
                }
            });
            addComponent(rightLaneText = new GUIComponentNumericTextBox(gui, leftOffset, topOffset + 30, String.valueOf(properties.rightLaneCount)) {
                @Override
                public void setVariable() {
                    controller.intersectionProperties.get(axis).rightLaneCount = getIntegerValue();
                }
            });
            addComponent(roadWidthText = new GUIComponentNumericTextBox(gui, leftOffset, topOffset + 40, String.valueOf(properties.roadWidth), 40) {
                @Override
                public void setVariable() {
                    controller.intersectionProperties.get(axis).roadWidth = getDoubleValue();
                }
            });
            addComponent(centerDistanceText = new GUIComponentNumericTextBox(gui, leftOffset, topOffset + 50, String.valueOf(properties.centerDistance), 40) {
                @Override
                public void setVariable() {
                    controller.intersectionProperties.get(axis).centerDistance = getDoubleValue();
                }
            });
            addComponent(centerOffsetText = new GUIComponentNumericTextBox(gui, leftOffset, topOffset + 60, String.valueOf(properties.centerOffset), 40) {
                @Override
                public void setVariable() {
                    controller.intersectionProperties.get(axis).centerOffset = getDoubleValue();
                }
            });
        }
    }
}
