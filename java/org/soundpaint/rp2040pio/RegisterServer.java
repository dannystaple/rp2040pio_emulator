/*
 * @(#)RegisterServer.java 1.00 21/03/03
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
package org.soundpaint.rp2040pio;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import org.soundpaint.rp2040pio.sdk.SDK;

/**
 * The idea of the RegisterServer class is to provide access to the
 * PIO emulator applicable even for processes other than the JVM
 * instance that hosts the PIO emulator, and potential integration
 * with other languages such as C/C++ or Python.  Effectively, this
 * class adds an architectural layer that provides the PIO emulator as
 * software as a service (SaaS).  Access is provided via a standard
 * TC/IP socket with a simple protocol for accessing the PIO
 * emulator's pseudo-memory-mapped registers, including the additional
 * emulator-specific extended set of registers (such as for accessing
 * the internal X and Y register or FIFO values).  For example, even
 * an ordinary C program (like one created with the pioasm tool) may
 * make access the PIO emulator by compiling it against a special
 * extended version of the Pico C SDK, such that e.g. set up and
 * control of the PIO Emulator can be done directly from the C code
 * injected in a .pio file.  Similarly, a Python library may be
 * developed that replaces the standard Pico Python libary with one
 * that accesses the emulator instead of real Pico hardware.
 *
 * The Pico Host SDL shows a specific example that draws the general
 * idea of how to extend the Pico C SDK in such a manner (see:
 * https://github.com/raspberrypi/pico-host-sdl).  For this PIO
 * emulator, the SDK is to be extended in a way similar to the Pico
 * Host SDL, such that access to the PIO's registers is not performed
 * via direct memory access (as the default implementation of the C
 * SDK does), but via the socket interface that this RegisterServer
 * class provides.
 */
public class RegisterServer
{
  public static final int DEFAULT_PORT_NUMBER = 1088;

  private static final String SERVER_VERSION = "RP PIO EMULATION V0.1";
  private static final String[] NULL_ARGS = new String[0];

  private final SDK sdk;
  private final int portNumber;
  private final ServerSocket serverSocket;
  private int connectionCounter;

  private RegisterServer()
  {
    throw new UnsupportedOperationException("unsupported empty constructor");
  }

  public RegisterServer(final SDK sdk) throws IOException
  {
    this(sdk, DEFAULT_PORT_NUMBER);
  }

  public RegisterServer(final SDK sdk, final int portNumber) throws IOException
  {
    this.sdk = sdk;
    this.portNumber = portNumber;
    serverSocket = new ServerSocket(portNumber);
    connectionCounter = 0;
    // TODO: Maybe introduce pool of client threads to limit maximum
    // number of connections.
    new Thread(() -> listen(), "RegisterServer Client Thread").start();
  }

  private void listen()
  {
    while (true) {
      try {
        final Socket clientSocket = serverSocket.accept();
        new Thread(() -> serve(clientSocket), "RegisterServer Server Thread").
          start();
      } catch (final IOException e) {
        // establishing connection failed => abort connection
      }
    }
  }

  private String getServerVersion()
  {
    return SERVER_VERSION;
  }

  private String getHelp()
  {
    return "available commands: ?, h, q, r, v";
  }

  private enum ResponseStatus
  {
    BYE("bye", 100),
    OK("ok", 101),
    ERR_UNKNOWN_COMMAND("unknown command", 400),
    ERR_MISSING_OPERAND("missing operand", 401),
    ERR_UNPARSED_INPUT("unparsed input", 402),
    ERR_NUMBER_EXPECTED("number expected", 403),
    ERR_UNEXPECTED("unexpected error", 404);

    private final String id;
    private final int code;

    private ResponseStatus(final String id, final int code)
    {
      if (id == null) {
        throw new NullPointerException("id");
      }
      this.id = id;
      this.code = code;
    }

    public String getId() { return id; }

    public int getCode() { return code; }

    public String getDisplayValue()
    {
      return code + " " + id.toUpperCase();
    }
  };

  private String createResponse(final ResponseStatus status)
  {
    return createResponse(status, null);
  }

  private String createResponse(final ResponseStatus status, final String msg)
  {
    if (status == null) {
      throw new NullPointerException("status");
    }
    final String statusDisplay = status.getDisplayValue();
    return msg != null ? statusDisplay + ": " + msg : statusDisplay;
  }

  private int parseUnsignedInt(final String unparsed)
  {
    if (unparsed.startsWith("0x") ||
        unparsed.startsWith("0X")) {
      return Integer.parseUnsignedInt(unparsed.substring(2), 16);
    } else {
      return Integer.parseUnsignedInt(unparsed);
    }
  }

  private String handleGetVersion(final String[] args)
  {
    if (args.length > 0) {
      return createResponse(ResponseStatus.ERR_UNPARSED_INPUT, args[0]);
    }
    return createResponse(ResponseStatus.OK, getServerVersion());
  }

  private String handleGetHelp(final String[] args)
  {
    if (args.length > 0) {
      return createResponse(ResponseStatus.ERR_UNPARSED_INPUT, args[0]);
    }
    return createResponse(ResponseStatus.OK, getHelp());
  }

  private String handleQuit(final String[] args)
  {
    if (args.length > 0) {
      return createResponse(ResponseStatus.ERR_UNPARSED_INPUT, args[0]);
    }
    return null;
  }

  private String handleRead(final String[] args)
  {
    if (args.length < 1) {
      return createResponse(ResponseStatus.ERR_MISSING_OPERAND, null);
    }
    if (args.length > 1) {
      return createResponse(ResponseStatus.ERR_UNPARSED_INPUT, args[1]);
    }
    final int address;
    try {
      address = parseUnsignedInt(args[0]);
    } catch (final NumberFormatException e) {
      return createResponse(ResponseStatus.ERR_NUMBER_EXPECTED, args[0]);
    }
    final int value = sdk.readAddress(address);
    return createResponse(ResponseStatus.OK, String.valueOf(value));
  }

  private String handleWrite(final String[] args)
  {
    if (args.length < 2) {
      return createResponse(ResponseStatus.ERR_MISSING_OPERAND, null);
    }
    if (args.length > 2) {
      return createResponse(ResponseStatus.ERR_UNPARSED_INPUT, args[2]);
    }
    final int address;
    try {
      address = parseUnsignedInt(args[0]);
    } catch (final NumberFormatException e) {
      return createResponse(ResponseStatus.ERR_NUMBER_EXPECTED, args[0]);
    }
    final int value;
    try {
      value = parseUnsignedInt(args[1]);
    } catch (final NumberFormatException e) {
      return createResponse(ResponseStatus.ERR_NUMBER_EXPECTED, args[1]);
    }
    sdk.writeAddress(address, value);
    return createResponse(ResponseStatus.OK);
  }

  private String handleIRQWait(final String[] args)
  {
    if (args.length < 1) {
      return createResponse(ResponseStatus.ERR_MISSING_OPERAND, null);
    }
    if (args.length > 1) {
      return createResponse(ResponseStatus.ERR_UNPARSED_INPUT, args[1]);
    }
    final int address;
    try {
      address = parseUnsignedInt(args[0]);
    } catch (final NumberFormatException e) {
      return createResponse(ResponseStatus.ERR_NUMBER_EXPECTED, args[0]);
    }
    sdk.irqWaitAddress(address);
    return createResponse(ResponseStatus.OK);
  }

  private String handleRequest(final String request)
  {
    if (request.isEmpty()) {
      return null;
    }
    final char command = request.charAt(0);
    final String unparsedArgs = request.substring(1);
    final String[] args =
      unparsedArgs.length() > 0 ? unparsedArgs.split(" ") : NULL_ARGS;
    /*
     * TODO: Idea: Introduce another command 's' for waiting (or
     * "sleeping") until the emulator runs idle (in MasterClock
     * SINGLE_STEP mode).  This way, an external application like
     * TimingDiagram does not need to busy wait (via periodic polling
     * with 'r' read command) until a triggered clock phase has been
     * completed.
     */
    switch (command) {
    case 'v':
      return handleGetVersion(args);
    case 'h':
    case '?':
      return handleGetHelp(args);
    case 'q':
      return handleQuit(args);
    case 'r':
      return handleRead(args);
    case 'w':
      return handleWrite(args);
    case 'i':
      return handleIRQWait(args);
    default:
      return createResponse(ResponseStatus.ERR_UNKNOWN_COMMAND,
                            String.valueOf(command));
    }
  }

  private void serve(final Socket clientSocket)
  {
    final int id = connectionCounter++;
    System.out.printf("connection #%d opened%n", id);
    PrintWriter out = null;
    try {
      out = new PrintWriter(clientSocket.getOutputStream(), true);
      final BufferedReader in =
        new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
      String request;
      while ((request = in.readLine()) != null) {
        System.out.println("Request: " + request);
        final String response = handleRequest(request.trim());
        if (response == null) {
          break;
        }
        System.out.println("Response: " + response);
        out.println(response);
      }
      System.out.printf("connection #%d closed%n", id);
    } catch (final Throwable t) {
      if (out != null) {
        try {
          out.println(createResponse(ResponseStatus.ERR_UNEXPECTED,
                                     t.getMessage()));
        } catch (final Throwable s) {
          // ignore
        }
      }
      System.out.printf("connection #%d aborted: %s%n", id, t);
    } finally {
      try {
        clientSocket.close();
      } catch (final IOException e) {
        System.out.println("warning: failed closing client socket: " + e);
      }
    }
  }
}

/*
 * Local Variables:
 *   coding:utf-8
 *   mode:Java
 * End:
 */
