/*
 * @(#)ActionPanel.java 1.00 21/04/06
 *
 * Copyright (C) 2021 Jürgen Reuter
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *
 * For updates and more info or contacting the author, visit:
 * <https://github.com/soundpaint/rp2040pio>
 *
 * Author's web site: www.juergen-reuter.de
 */
package org.soundpaint.rp2040pio.diagram;

import java.awt.Dimension;
import java.awt.Frame;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.io.IOException;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.SpinnerModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JSpinner;

public class ActionPanel extends Box
{
  private static final long serialVersionUID = -4136799373128393432L;
  private static final int defaultCycles = 30;

  private final JLabel lbCycles;
  private final SpinnerModel cyclesModel;
  private final JSpinner spCycles;
  private final JButton btExecute;
  private final JButton btClose;

  public ActionPanel(final TimingDiagram timingDiagram)
  {
    super(BoxLayout.X_AXIS);
    lbCycles = new JLabel("Cycles");
    add(lbCycles);
    add(Box.createHorizontalStrut(5));
    cyclesModel = new SpinnerNumberModel(defaultCycles, 1, 1000, 1);
    spCycles = new JSpinner(cyclesModel);
    final int spCyclesHeight = spCycles.getPreferredSize().height;
    spCycles.setMaximumSize(new Dimension(100, spCyclesHeight));
    add(spCycles);
    add(Box.createHorizontalStrut(5));
    btExecute = new JButton("Execute");
    btExecute.setMnemonic(KeyEvent.VK_E);
    btExecute.addActionListener((event) -> {
        final int cycles = (Integer)spCycles.getValue();
        try {
          if (cycles == 10) timingDiagram.clear();
          else timingDiagram.createSnapShot(cycles);
        } catch (final IOException e) {
          final String title = "Failed Creating Snapshot";
          final String message = "I/O Error: " + e.getMessage();
          JOptionPane.showMessageDialog(this, message, title,
                                        JOptionPane.WARNING_MESSAGE);
          timingDiagram.clear();
        }
      });
    add(btExecute);
    add(Box.createHorizontalGlue());
    btClose = new JButton("Close");
    btClose.setMnemonic(KeyEvent.VK_C);
    btClose.addActionListener((event) -> {
        final WindowEvent closeEvent =
          new WindowEvent(timingDiagram, WindowEvent.WINDOW_CLOSING);
        timingDiagram.dispatchEvent(closeEvent);
      });
    add(btClose);
  }
}