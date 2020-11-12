package org.eientei.gtce2oc.impl;

import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.config.Configuration;

import java.util.Arrays;

public class MachineConfig {
    private EnumFacing[] inputs = new EnumFacing[]{
            EnumFacing.UP,
            EnumFacing.DOWN,
            EnumFacing.NORTH,
            EnumFacing.EAST,
            EnumFacing.SOUTH,
            EnumFacing.WEST
    };
    private EnumFacing[] outputs = new EnumFacing[]{};
    private long inputAmperage = 1;
    private long outputAmperage = 1;
    private long inputVoltage = 128;
    private long outputVoltage = 128;

    public static String[] VALID_SIDES = namesBySides(EnumFacing.values());

    public static String[] namesBySides(EnumFacing[] values) {
        return Arrays.stream(values).map(EnumFacing::name).toArray(String[]::new);
    }

    public static EnumFacing[] sidesByNames(String[] values) {
        return Arrays.stream(values).map(EnumFacing::valueOf).toArray(EnumFacing[]::new);
    }

    public static EnumFacing[] getSides(Configuration config, String category, String key, EnumFacing[] defaults) {
        return getSides(config, category, key, defaults,
                "Sides from which " + category + " will accept energy. Valid values are: "
                        + Arrays.toString(VALID_SIDES));
    }

    public static EnumFacing[] getSides(
            Configuration config,
            String category,
            String key,
            EnumFacing[] defaults,
            String comment
    ) {
        return sidesByNames(config.getStringList(key, category, namesBySides(defaults), comment, VALID_SIDES));
    }

    public MachineConfig() {
    }

    public MachineConfig(
            Configuration config,
            String category,
            int defaultVoltage,
            int defaultAmperage,
            EnumFacing[] defaultInputs,
            EnumFacing[] defaultOutputs
    ) {
        inputs = getSides(config, category, "inputs", defaultInputs);
        outputs = getSides(config, category, "outputs", defaultOutputs);
        inputAmperage = config.getInt(
                "input_amperage",
                category,
                defaultAmperage,
                0,
                Integer.MAX_VALUE,
                "Amperage " + category + " will accept as input in one tick"
        );
        outputAmperage = config.getInt(
                "output_amperage",
                category,
                defaultAmperage,
                0,
                Integer.MAX_VALUE,
                "Amperage " + category + " will emmit if stored enough energy"
        );
        inputVoltage = config.getInt(
                "input_voltage",
                category,
                defaultVoltage,
                0,
                Integer.MAX_VALUE,
                "Voltage " + category + " will accept as input without blowing"
        );
        outputVoltage = config.getInt(
                "output_voltage",
                category,
                defaultVoltage,
                0,
                Integer.MAX_VALUE,
                "Voltage " + category + " will emmit if stored enough energy"
        );
    }

    public EnumFacing[] getInputs() {
        return inputs;
    }

    public void setInputs(EnumFacing[] inputs) {
        this.inputs = inputs;
    }

    public EnumFacing[] getOutputs() {
        return outputs;
    }

    public void setOutputs(EnumFacing[] outputs) {
        this.outputs = outputs;
    }

    public long getInputAmperage() {
        return inputAmperage;
    }

    public void setInputAmperage(long inputAmperage) {
        this.inputAmperage = inputAmperage;
    }

    public long getOutputAmperage() {
        return outputAmperage;
    }

    public void setOutputAmperage(long outputAmperage) {
        this.outputAmperage = outputAmperage;
    }

    public long getInputVoltage() {
        return inputVoltage;
    }

    public void setInputVoltage(long inputVoltage) {
        this.inputVoltage = inputVoltage;
    }

    public long getOutputVoltage() {
        return outputVoltage;
    }

    public void setOutputVoltage(long outputVoltage) {
        this.outputVoltage = outputVoltage;
    }
}
