/**
 * Client connections, I/O loops, and session state.
 * <p>
 * {@link ClientConnectionManager} tracks live sockets (registration, limits, idle timeouts, and
 * shutdown cleanup). Uses NIO channels; higher layers remain unaware of socket details.
 */
package com.myredis.network;
