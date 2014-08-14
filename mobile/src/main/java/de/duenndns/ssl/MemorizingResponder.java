package de.duenndns.ssl;

/**
 * Created by belovictor on 6/9/13.
 */
public interface MemorizingResponder {
    public void makeDecision(int decisionId, String certMessage);
}
