/*
 * Copyright 2017-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.server;

import io.atomix.Atomix;
import io.atomix.cluster.Node;
import io.atomix.cluster.NodeId;
import io.atomix.messaging.Endpoint;
import io.atomix.messaging.netty.NettyMessagingService;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.Argument;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.ArgumentType;
import net.sourceforge.argparse4j.inf.Namespace;

import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

/**
 * Atomix server.
 */
public class AtomixServer {
  public static void main(String[] args) throws Exception {
    ArgumentType<Node> nodeType = new ArgumentType<Node>() {
      @Override
      public Node convert(ArgumentParser argumentParser, Argument argument, String value) throws ArgumentParserException {
        String[] address = parseAddress(value);
        return Node.newBuilder()
            .withId(parseNodeId(address))
            .withEndpoint(parseEndpoint(address))
            .build();
      }
    };

    ArgumentType<File> fileType = new ArgumentType<File>() {
      @Override
      public File convert(ArgumentParser argumentParser, Argument argument, String value) throws ArgumentParserException {
        return new File(value);
      }
    };

    ArgumentParser parser = ArgumentParsers.newArgumentParser("AtomixServer")
        .defaultHelp(true)
        .description("Atomix server");
    parser.addArgument("address")
        .required(true)
        .type(nodeType)
        .metavar("NAME:HOST:PORT")
        .help("The server address");
    parser.addArgument("--bootstrap", "-b")
        .nargs("*")
        .metavar("NAME:HOST:PORT")
        .type(nodeType)
        .help("Bootstraps a new cluster");
    parser.addArgument("--http-port", "-p")
        .metavar("PORT")
        .required(false)
        .type(Integer.class)
        .setDefault(5678)
        .help("An optional HTTP server port");
    parser.addArgument("--data-dir", "-d")
        .required(false)
        .type(fileType)
        .setDefault(new File(System.getProperty("user.dir"), "data"))
        .help("The server data directory");

    Namespace namespace = null;
    try {
      namespace = parser.parseArgs(args);
    } catch (ArgumentParserException e) {
      parser.handleError(e);
      System.exit(1);
    }

    Node localNode = namespace.get("address");
    List<Node> bootstrap = namespace.getList("bootstrap");
    File dataDir = namespace.get("data-dir");
    Integer httpPort = namespace.getInt("http-port");

    Atomix atomix = Atomix.newBuilder()
        .withLocalNode(localNode)
        .withBootstrapNodes(bootstrap)
        .withDataDir(dataDir)
        .withHttpPort(httpPort)
        .build();

    atomix.open().join();

    synchronized (Atomix.class) {
      while (atomix.isOpen()) {
        Atomix.class.wait();
      }
    }
  }

  private static String[] parseAddress(String address) {
    String[] parsed = address.split(":");
    if (parsed.length > 3) {
      throw new IllegalArgumentException("Malformed address " + address);
    }
    return parsed;
  }

  private static NodeId parseNodeId(String[] address) {
    if (address.length == 3) {
      return NodeId.from(address[0]);
    } else if (address.length == 2) {
      return NodeId.from(parseEndpoint(address).toString());
    } else {
      try {
        InetAddress.getByName(address[0]);
        return NodeId.from(parseEndpoint(address).toString());
      } catch (UnknownHostException e) {
        return NodeId.from(address[0]);
      }
    }
  }

  private static Endpoint parseEndpoint(String[] address) {
    String host;
    int port;
    if (address.length == 3) {
      host = address[1];
      port = Integer.parseInt(address[2]);
    } else if (address.length == 2) {
      try {
        host = address[0];
        port = Integer.parseInt(address[1]);
      } catch (NumberFormatException e) {
        host = address[1];
        port = NettyMessagingService.DEFAULT_PORT;
      }
    } else {
      host = address[0];
      port = NettyMessagingService.DEFAULT_PORT;
    }

    try {
      return new Endpoint(InetAddress.getByName(host), port);
    } catch (UnknownHostException e) {
      throw new IllegalArgumentException("Failed to resolve host", e);
    }
  }
}
