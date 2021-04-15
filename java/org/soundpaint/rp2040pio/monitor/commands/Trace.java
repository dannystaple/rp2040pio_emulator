/*
 * @(#)Trace.java 1.00 21/03/28
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
package org.soundpaint.rp2040pio.monitor.commands;

import java.io.IOException;
import java.io.PrintStream;
import org.soundpaint.rp2040pio.Bit;
import org.soundpaint.rp2040pio.CmdOptions;
import org.soundpaint.rp2040pio.Constants;
import org.soundpaint.rp2040pio.Direction;
import org.soundpaint.rp2040pio.GPIOIOBank0Registers;
import org.soundpaint.rp2040pio.PIOEmuRegisters;
import org.soundpaint.rp2040pio.PinState;
import org.soundpaint.rp2040pio.monitor.Command;
import org.soundpaint.rp2040pio.monitor.MonitorUtils;
import org.soundpaint.rp2040pio.sdk.SDK;

/**
 * Monitor command "trace" lets the emulator execute clock cycles step
 * by step.
 */
public class Trace extends Command
{
  private static final String fullName = "trace";
  private static final String singleLineDescription =
    "trace program by performing a number of clock cycles";

  private static final CmdOptions.IntegerOptionDeclaration optCycles =
    CmdOptions.createIntegerOption("COUNT", false, 'c', "cycles", 1,
                                   "number of cycles to apply");
  private static final CmdOptions.IntegerOptionDeclaration optPio =
    CmdOptions.createIntegerOption("NUMBER", false, 'p', "pio", null,
                                   "limit option -a to PIO number, either " +
                                   "0 or 1 or both, if undefined");
  private static final CmdOptions.IntegerOptionDeclaration optSm =
    CmdOptions.createIntegerOption("NUMBER", false, 's', "sm", null,
                                   "limit option -a to SM number, one of " +
                                   "0, 1, 2 or 3, or all, if undefined");
  private static final CmdOptions.FlagOptionDeclaration optPc =
    CmdOptions.createFlagOption(false, 'a', "address", CmdOptions.Flag.OFF,
                                "show instruction address (PC values) for " +
                                "selected state machines of selected PIOs");
  private static final CmdOptions.FlagOptionDeclaration optGpio =
    CmdOptions.createFlagOption(false, 'g', "gpio", CmdOptions.Flag.OFF,
                                "show status of GPIO pins");
  private static final CmdOptions.IntegerOptionDeclaration optWait =
    CmdOptions.createIntegerOption("NUMBER", false, 'w', "wait", 0,
                                   "before each cycle, sleep for the " +
                                   "specified time [ms] or until interrupted");

  private static final int[][] addressPioSmPc = {{
      PIOEmuRegisters.getAddress(0, PIOEmuRegisters.Regs.SM0_PC),
      PIOEmuRegisters.getAddress(0, PIOEmuRegisters.Regs.SM1_PC),
      PIOEmuRegisters.getAddress(0, PIOEmuRegisters.Regs.SM2_PC),
      PIOEmuRegisters.getAddress(0, PIOEmuRegisters.Regs.SM3_PC)
    }, {
      PIOEmuRegisters.getAddress(1, PIOEmuRegisters.Regs.SM0_PC),
      PIOEmuRegisters.getAddress(1, PIOEmuRegisters.Regs.SM1_PC),
      PIOEmuRegisters.getAddress(1, PIOEmuRegisters.Regs.SM2_PC),
      PIOEmuRegisters.getAddress(1, PIOEmuRegisters.Regs.SM3_PC)
    }};

  private final SDK sdk;

  public Trace(final PrintStream console, final SDK sdk)
  {
    super(console, fullName, singleLineDescription,
          new CmdOptions.OptionDeclaration<?>[]
          { optPio, optSm, optCycles, optPc, optGpio, optWait });
    if (sdk == null) {
      throw new NullPointerException("sdk");
    }
    this.sdk = sdk;
  }

  @Override
  protected void checkValidity(final CmdOptions options)
    throws CmdOptions.ParseException
  {
    final Integer optPioValue = options.getValue(optPio);
    if (optPioValue != null) {
      final int pioNum = optPioValue;
      if ((pioNum < 0) || (pioNum > Constants.PIO_NUM - 1)) {
        throw new CmdOptions.
          ParseException("PIO number must be either 0 or 1, if defined");
      }
    }
    final Integer optSmValue = options.getValue(optSm);
    if (optSmValue != null) {
      final int smNum = optSmValue;
      if ((smNum < 0) || (smNum > Constants.SM_COUNT - 1)) {
        throw new CmdOptions.
          ParseException("SM number must be one of 0, 1, 2 or 3, if defined");
      }
    }
    final int cycles = options.getValue(optCycles);
    if (cycles < 0) {
      throw new CmdOptions.
        ParseException("COUNT must be a non-negative value", optCycles);
    }
    final int wait = options.getValue(optWait);
    if (wait < 0) {
      throw new CmdOptions.
        ParseException("NUMBER must be a non-negative value", optWait);
    }
  }

  private void displayPcValues(final int pioNumFirst,
                               final int pioNumLast,
                               final int smNumFirst,
                               final int smNumLast)
    throws IOException
  {
    for (int pioNum = pioNumFirst; pioNum <= pioNumLast; pioNum++) {
      for (int smNum = smNumFirst; smNum <= smNumLast; smNum++) {
        console.printf("(pio%d:sm%d) PC=%02x%n", pioNum, smNum,
                       sdk.readAddress(addressPioSmPc[pioNum][smNum]));
      }
    }
  }

  private void displayGpioValues()
    throws IOException
  {
    final PinState[] pinStates = sdk.getGPIOSDK().getPinStates();
    final String gpioPinBits = MonitorUtils.asBitArrayDisplay(pinStates);
    console.printf("(pio*:sm*) %s%n", gpioPinBits);
  }

  /**
   * Returns true if no error occurred and the command has been
   * executed.
   */
  @Override
  protected boolean execute(final CmdOptions options) throws IOException
  {
    final Integer optPioValue = options.getValue(optPio);
    final Integer optSmValue = options.getValue(optSm);
    final int pioNumFirst = optPioValue != null ? optPioValue : 0;
    final int pioNumLast =
      optPioValue != null ? optPioValue : Constants.PIO_NUM - 1;
    final int smNumFirst = optSmValue != null ? optSmValue : 0;
    final int smNumLast =
      optSmValue != null ? optSmValue : Constants.SM_COUNT - 1;
    final int cycles = options.getValue(optCycles);
    final int wait = options.getValue(optWait);
    for (int i = 0; i < cycles; i++) {
      if (wait > 0) {
        try {
          Thread.sleep(wait);
        } catch (final InterruptedException e) {
          // ignore
        }
      }
      sdk.triggerCyclePhase0(true);
      sdk.triggerCyclePhase1(true);
      if (options.getValue(optPc).isOn()) {
        displayPcValues(pioNumFirst, pioNumLast, smNumFirst, smNumLast);
      }
      if (options.getValue(optGpio).isOn()) {
        displayGpioValues();
      }
    }
    console.println(cycles + " clock cycle" + (cycles != 1 ? "s" : "") +
                    " executed.");
    return true;
  }
}

/*
 * Local Variables:
 *   coding:utf-8
 *   mode:Java
 * End:
 */
