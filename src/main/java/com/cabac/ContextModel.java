package com.cabac;

public class ContextModel {

    private int stateIdx;
    private int valMPS;

    public ContextModel() {
        this.stateIdx = 0;
        this.valMPS = 0;
    }

    public int getStateIdx() { return stateIdx; }
    public int getValMPS() { return valMPS; }

    public void updateOnMPS() {
        stateIdx = Tables.TRANS_IDX_MPS[stateIdx];
    }

    public void updateOnLPS() {
        if (stateIdx == 0) {
            valMPS = 1 - valMPS;
        }
        stateIdx = Tables.TRANS_IDX_LPS[stateIdx];
    }
}