
/*******************************************************************************
 * Copyright (c) 2006 Wind River Systems, Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Wind River Systems, Inc. - initial implementation
 *     
 *******************************************************************************/


package org.eclipse.tm.terminal;

public class TerminalActionClearAll extends TerminalAction
{
    protected TerminalActionClearAll(TerminalTarget target)
    {
        super(target,
              ON_EDIT_CLEARALL,
              TerminalActionClearAll.class.getName());

        setupAction(TERMINAL_TEXT_CLEARALL,
                    TERMINAL_TEXT_CLEARALL,
                    null,
                    null,
                    null,
                    false);
    }
}


