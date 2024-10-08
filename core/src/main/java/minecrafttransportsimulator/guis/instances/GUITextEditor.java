package minecrafttransportsimulator.guis.instances;

import minecrafttransportsimulator.baseclasses.ColorRGB;
import minecrafttransportsimulator.blocks.tileentities.instances.TileEntityPole_Sign;
import minecrafttransportsimulator.entities.components.AEntityD_Definable;
import minecrafttransportsimulator.entities.components.AEntityF_Multipart;
import minecrafttransportsimulator.entities.instances.APart;
import minecrafttransportsimulator.guis.components.*;
import minecrafttransportsimulator.jsondefs.JSONText;
import minecrafttransportsimulator.mcinterface.InterfaceManager;
import minecrafttransportsimulator.packets.instances.PacketEntityTextChange;
import minecrafttransportsimulator.rendering.RenderText.TextAlignment;
import minecrafttransportsimulator.systems.LanguageSystem;

import java.util.*;

public class GUITextEditor extends AGUIBase {
    //Input boxes and their field names.
    private final Map<String, GUIComponentTextBox> textInputBoxes = new HashMap<>();
    //Entity clicked.
    private final AEntityD_Definable<?> entity;
    //Labels for sign.  These do fancy rendering.
    private final Map<GUIComponentLabel, String> signTextLabels = new HashMap<>();
    //Buttons.
    private GUIComponentButton confirmButton;

    public GUITextEditor(AEntityD_Definable<?> entity) {
        super();
        this.entity = entity;
    }

    @Override
    public void setupComponents() {
        super.setupComponents();
        int boxWidth;
        List<JSONText> textObjects;
        List<String> textLines;
        textInputBoxes.clear();
        if (entity instanceof TileEntityPole_Sign) {
            //Add the render to render the sign.
            GUIComponent3DModel modelRender = new GUIComponent3DModel(guiLeft + 3 * getWidth() / 4, guiTop + 160, 64.0F, false, false, false);
            addComponent(modelRender);
            modelRender.modelLocation = entity.definition.getModelLocation(entity.subDefinition);
            modelRender.textureLocation = entity.definition.getTextureLocation(entity.subDefinition);

            //Set text and text objects.
            boxWidth = 100;
            textObjects = new ArrayList<>(entity.text.keySet());
            textLines = new ArrayList<>(entity.text.values());

            //Add render-able labels for the sign object.
            signTextLabels.clear();
            for (byte i = 0; i < textObjects.size(); ++i) {
                JSONText textDef = textObjects.get(i);
                GUIComponentLabel label = new GUIComponentLabel(modelRender.constructedX + (int) (textDef.pos.x * 64F), modelRender.constructedY - (int) (textDef.pos.y * 64F), textDef.color, textLines.get(i), TextAlignment.values()[textDef.renderPosition], textDef.scale * 64F / 16F, textDef.wrapWidth * 64 / 16, textDef.fontName, textDef.autoScale);
                addComponent(label);
                signTextLabels.put(label, textDef.fieldName);
            }
        } else {
            boxWidth = 200;
            textObjects = new ArrayList<>(entity.text.keySet());
            textLines = new ArrayList<>(entity.text.values());

            //Add part text objects if we are a multipart.
            if (entity instanceof AEntityF_Multipart) {
                for (APart part : ((AEntityF_Multipart<?>) entity).allParts) {
                    textObjects.addAll(part.text.keySet());
                    textLines.addAll(part.text.values());
                }
            }
        }

        //Add text box components for every text.  Paired with labels to render the text name above the boxes.
        //Don't add multiple boxes per text field, however.  Those use the same box.
        int currentOffset = 0;
        for (JSONText textObject : textObjects) {
            if (textObject.variableName == null && !textInputBoxes.containsKey(textObject.fieldName)) {
                //No text box present for the field name.  Create a new one.
                GUIComponentLabel label = new GUIComponentLabel(guiLeft + 20, guiTop + 30 + currentOffset, ColorRGB.BLACK, textObject.fieldName);
                addComponent(label);
                int textRowsRequired = 1 + 5 * textObject.maxLength / boxWidth;
                GUIComponentTextBox box = new GUIComponentTextBox(this, guiLeft + 20, label.constructedY + 10, boxWidth, 12 * textRowsRequired, textLines.get(textObjects.indexOf(textObject)), ColorRGB.WHITE, textObject.maxLength);
                addComponent(box);
                textInputBoxes.put(textObject.fieldName, box);
                currentOffset += box.height + 12;
            }
        }

        //Add the confirm button.
        addComponent(confirmButton = new GUIComponentButton(this, guiLeft + 150, guiTop + 15, 80, 20, LanguageSystem.GUI_CONFIRM.getCurrentValue()) {
            @Override
            public void onClicked(boolean leftSide) {
                LinkedHashMap<String, String> packetTextLines = new LinkedHashMap<String, String>();
                for (JSONText textObject : textObjects) {
                    if (textObject.variableName == null) {
                        packetTextLines.put(textObject.fieldName, textInputBoxes.get(textObject.fieldName).getText());
                    }
                }
                InterfaceManager.packetInterface.sendToServer(new PacketEntityTextChange(entity, packetTextLines));
                close();
            }
        });
    }

    @Override
    public void setStates() {
        super.setStates();
        confirmButton.enabled = true;
        signTextLabels.forEach((label, fieldName) -> {
            label.text = textInputBoxes.get(fieldName).getText();
        });
    }

    @Override
    protected boolean canStayOpen() {
        return super.canStayOpen() && entity.isValid;
    }
}
