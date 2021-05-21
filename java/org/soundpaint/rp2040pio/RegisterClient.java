/*
 * @(#)RegisterClient.java 1.00 21/03/17
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
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import org.soundpaint.rp2040pio.sdk.SDK;

/**
 * Remote client that connects to a RegisterServer via TCP/IP socket
 * connection.
 */
public class RegisterClient extends AbstractRegisters
{
  private static final String MSG_NO_CONNECTION = "no connection";

  private static class Response
  {
    private final PrintStream console;
    private final int statusCode;
    private final String statusId;
    private final String result;

    private Response()
    {
      throw new UnsupportedOperationException("unsupported empty constructor");
    }

    private Response(final PrintStream console,
                     final int statusCode, final String statusId,
                     final String result)
    {
      if (console == null) {
        throw new NullPointerException("console");
      }
      this.console = console;
      this.statusCode = statusCode;
      this.statusId = statusId;
      this.result = result;
    }

    public int getStatusCode()
    {
      return statusCode;
    }

    public String getStatusId()
    {
      return statusId;
    }

    public String getResult()
    {
      return result;
    }

    public boolean isOk()
    {
      return statusCode == 101; // TODO: Use global constant.
    }

    public String getResultOrThrowOnFailure(final String errorMessage)
      throws IOException
    {
      if (!isOk()) {
        final String responseMessage = errorMessage + ": " + toString();
        console.println(responseMessage);
        throw new IOException(responseMessage);
      }
      return result;
    }

    @Override
    public String toString()
    {
      return
        statusCode + " " + statusId + (result != null ? ": " + result : "");
    }
  }

  private final PrintStream console;
  private final Socket socket;

  /**
   * Creates a register client, but does not yet connect to any
   * emulation server.
   *
   * @see #connect
   */
  public RegisterClient(final PrintStream console) throws IOException
  {
    super(0x0, (short)0x0);
    if (console == null) {
      throw new NullPointerException("console");
    }
    this.console = console;
    socket = new Socket();
  }

  /**
   * Creates register client and connects to the default port of the
   * specified host.  If host is null, connects to localhost.
   */
  public RegisterClient(final PrintStream console, final String host)
    throws IOException
  {
    this(console);
    connect(host);
  }

  /**
   * Creates register client and connects to the specified port of the
   * specified host.  If host is null, connects to localhost.
   */
  public RegisterClient(final PrintStream console,
                        final String host, final int port)
    throws IOException
  {
    this(console);
    connect(host, port);
  }

  /**
   * Connects this register client to the default port of the
   * specified host.  If host is null, connects to localhost.
   */
  public void connect(final String host)
    throws IOException
  {
    connect(host, Constants.REGISTER_SERVER_DEFAULT_PORT_NUMBER);
  }

  /**
   * Connects this register client to the specified port of the
   * specified host.  If host is null, connects to localhost.
   */
  public void connect(final String host, final int port)
    throws IOException
  {
    socket.connect(host != null ?
                   new InetSocketAddress(host, port) :
                   new InetSocketAddress(InetAddress.getByName(null), port));
  }

  private synchronized Response getResponse(final String request)
    throws IOException
  {
    final PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
    final BufferedReader in =
      new BufferedReader(new InputStreamReader(socket.getInputStream()));
    out.println(request);
    final String response = in.readLine();
    if (response == null) {
      return null;
    }
    final int colonPos = response.indexOf(':');
    final String statusDisplay =
      colonPos >= 0 ? response.substring(0, colonPos) : response;
    final int spacePos = statusDisplay.indexOf(' ');
    if (spacePos < 0) {
      throw new IOException("failed parsing server response status: " +
                            statusDisplay);
    }
    final String statusCodeAsString = statusDisplay.substring(0, spacePos);
    final int statusCode;
    try {
      statusCode = Integer.parseInt(statusCodeAsString);
    } catch (final NumberFormatException e) {
      throw new IOException("failed parsing server response status code: " +
                            statusCodeAsString);
    }
    final String statusId = statusDisplay.substring(spacePos + 1).trim();
    final String result =
      colonPos >= 0 ? response.substring(colonPos + 1).trim() : null;
    return new Response(console, statusCode, statusId, result);
  }

  private void checkResponse(final Response response) throws IOException
  {
    if (response == null) {
      throw new IOException(MSG_NO_CONNECTION);
    }
  }

  @Override
  public String getVersion() throws IOException
  {
    final Response response = getResponse("v");
    checkResponse(response);
    return response.getResultOrThrowOnFailure("failed retreiving version");
  }

  public String getHelp() throws IOException
  {
    final Response response = getResponse("h");
    checkResponse(response);
    return response.getResultOrThrowOnFailure("failed retreiving help");
  }

  public void quit() throws IOException
  {
    final Response response = getResponse("q");
    if (response != null) {
      throw new IOException("unexpected response on quit: " + response);
    }
  }

  @Override
  public int getBaseAddress() { return 0; }

  @Override
  public boolean providesAddress(final int address) throws IOException
  {
    final String request = String.format("p 0x%08x", address);
    final Response response = getResponse(request);
    checkResponse(response);
    final String provisionRetrievalMessage =
      String.format("failed retrieving provision info for address 0x%08x",
                    address);
    final String result =
      response.getResultOrThrowOnFailure(provisionRetrievalMessage);
    if (result == null) {
      final String message =
        String.format("missing provision info for address 0x%08x", address);
      throw new IOException(message);
    }
    final boolean provided;
    try {
      provided = Boolean.parseBoolean(result);
    } catch (final NumberFormatException e) {
      final String message =
        String.format("failed parsing provision info for address 0x%08x: %s",
                      address, result);
      throw new IOException(message);
    }
    return provided;
  }

  @Override
  protected <T extends Enum<T>> T[] getRegs() {
    throw new InternalError("method not applicable for this class");
  }

  @Override
  public String getAddressLabel(final int address) throws IOException
  {
    final String request = String.format("l 0x%08x", address);
    final Response response = getResponse(request);
    checkResponse(response);
    final String labelRetrievalMessage =
      String.format("failed retrieving label for address 0x%08x", address);
    final String result =
      response.getResultOrThrowOnFailure(labelRetrievalMessage);
    if (result == null) {
      final String message =
        String.format("missing label for address 0x%08x", address);
      throw new IOException(message);
    }
    return result;
  }

  @Override
  protected void writeRegister(final int regNum,
                               final int bits, final int mask,
                               final boolean xor)
  {
    throw new InternalError("method not applicable for this class");
  }

  @Override
  public void writeAddress(final int address, final int value)
    throws IOException
  {
    final String request = String.format("w 0x%08x 0x%08x", address, value);
    final Response response = getResponse(request);
    checkResponse(response);
    final String message =
      String.format("failed writing value 0x%04x to address 0x%08x",
                    value, address);
    response.getResultOrThrowOnFailure(message);
  }

  private int parseIntResult(final int address, final String result)
    throws IOException
  {
    if (result == null) {
      final String message =
        String.format("missing value for address 0x%08x", address);
      throw new IOException(message);
    }
    final int value;
    try {
      value = Integer.parseInt(result);
    } catch (final NumberFormatException e) {
      final String message =
        String.format("failed parsing value for address 0x%08x: %s",
                      address, result);
      throw new IOException(message);
    }
    return value;
  }

  @Override
  protected int readRegister(final int regNum)
  {
    throw new InternalError("method not applicable for this class");
  }

  @Override
  public int readAddress(final int address) throws IOException
  {
    final String request = String.format("r 0x%08x", address);
    final Response response = getResponse(request);
    checkResponse(response);
    final String message =
      String.format("failed retrieving value for address 0x%08x", address);
    final String result =
      response.getResultOrThrowOnFailure(message);
    return parseIntResult(address, result);
  }

  @Override
  public int wait(final int address,
                  final int expectedValue, final int mask,
                  final long cyclesTimeout,
                  final long millisTimeout)
    throws IOException
  {
    final StringBuffer query = new StringBuffer();
    final String request =
      String.format("i 0x%08x 0x%08x 0x%08x %d %d",
                    address, expectedValue, mask, cyclesTimeout, millisTimeout);
    final Response response = getResponse(request);
    checkResponse(response);
    final String message =
      String.format("failed waiting for IRQ on address 0x%08x", address);
    final String result =
      response.getResultOrThrowOnFailure(message);
    return parseIntResult(address, result);
  }
}

/*
 * Local Variables:
 *   coding:utf-8
 *   mode:Java
 * End:
 */
