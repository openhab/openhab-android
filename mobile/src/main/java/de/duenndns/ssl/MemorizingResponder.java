/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package de.duenndns.ssl;

/**
 * Created by belovictor on 6/9/13.
 */
public interface MemorizingResponder {
    public void makeDecision(int decisionId, String certMessage);
}
