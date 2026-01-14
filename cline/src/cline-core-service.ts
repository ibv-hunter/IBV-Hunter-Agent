import {ChildProcess, spawn} from 'child_process';
import * as grpc from "@grpc/grpc-js"
import * as health from "grpc-health-check"
import * as path from 'path';
import * as net from 'net';

import {host} from './support/index.ts';
import * as hostbridge from "./hostbridge/index.ts";

const HOME_DIR = process.env.HOME || process.env.USERPROFILE!;
const CORE_DIR = path.join(HOME_DIR, '.cline', 'core');
const INSTALL_DIR = path.join(CORE_DIR, 'dev-instance');

export let isShuttingDown = false;

let hostBridgeServer: grpc.Server = new grpc.Server();
let clineCoreChild: ChildProcess | undefined = undefined;

function getPlatformName(): string {
  const os = process.platform;
  const arch = process.arch;

  if (os === 'darwin' && arch === 'x64') {
    return 'darwin-x64';
  }
  if (os === 'darwin' && arch === 'arm64') {
    return 'darwin-arm64';
  }
  if (os === 'win32' && arch === 'x64') {
    return 'win-x64';
  }
  if (os === 'linux' && arch === 'x64') {
    return 'linux-x64';
  }

  throw new Error(`Unsupported platform: ${os} ${arch}`);
}

function getRandomPort(): Promise<number> {
  return new Promise((resolve, reject) => {
    const server = net.createServer();

    server.listen(0, '127.0.0.1', () => {
      const address = server.address();

      if (typeof address === 'string' || address === null) {
        server.close(() => reject(new Error("Failed to get port from OS")));
        return;
      }

      const port = address.port;

      server.close(() => {
        resolve(port);
      });
    });

    server.on('error', (err) => {
      server.close(() => reject(err));
    });
  });
}

function waitForPort(port: number, timeoutMs: number = 60000, intervalMs: number = 100): Promise<void> {
  return new Promise((resolve, reject) => {
    let attempts = Math.ceil(timeoutMs / intervalMs);

    const checkPort = () => {
      if (attempts-- <= 0) {
        return reject(new Error(`Port ${port} was not open within ${timeoutMs}ms`));
      }

      const socket = new net.Socket();

      socket.once('connect', () => {
        socket.end();
        resolve();
      });

      socket.once('error', () => {
        setTimeout(checkPort, intervalMs);
      });

      socket.setTimeout(intervalMs);

      socket.connect(port, '127.0.0.1');
    };

    checkPort();
  });
}

export async function startCline(): Promise<number> {
  // Set up health check
  const healthImpl = new health.HealthImplementation({ "": "SERVING" })
  healthImpl.addToServer(hostBridgeServer)

  // Add host bridge services using the real implementations
  hostBridgeServer.addService(host.WorkspaceServiceService, new hostbridge.WorkspaceServiceImpl())
  hostBridgeServer.addService(host.WindowServiceService, new hostbridge.WindowServiceImpl())
  hostBridgeServer.addService(host.EnvServiceService, new hostbridge.EnvServiceImpl())
  hostBridgeServer.addService(host.DiffServiceService, new hostbridge.DiffServiceImpl())
  hostBridgeServer.addService(host.TestingServiceService, new hostbridge.TestingServiceImpl())

  return new Promise((resolve, reject) => {
    hostBridgeServer.bindAsync("127.0.0.1:0", grpc.ServerCredentials.createInsecure(), async (err, port) => {
      if (err) {
        console.error(`Failed to bind host bridge server to 127.0.0.1:0`, err);
        hostBridgeServer.forceShutdown();
        return reject(err);
      }
      try {
        const corePort = await getRandomPort();

        let hasResolved = false;
        const child = await startClineCoreService(corePort, port);

        child.on('error', (error) => {
          hostBridgeServer.forceShutdown();
          reject(new Error(`Failed to run Cline Core service: ${error.message}`));
        });

        child.on('exit', (code, signal) => {
          if (isShuttingDown) return

          const reason = code !== null ? `exit code ${code}` : `signal ${signal}`;
          const error = new Error(`Cline Core service exited unexpectedly with ${reason}.`);

          if (hasResolved) {
            console.error(`CRITICAL: Service crash detected after startup.`, error);
            exitWithCode(1)
          } else {
            hostBridgeServer.forceShutdown();
            reject(error);
          }
        });
        clineCoreChild = child

        const sigHandler = () => exitWithCode(0);

        process.on('SIGTERM', sigHandler);
        process.on('SIGINT', sigHandler);

        await waitForPort(corePort);

        hasResolved = true;
        resolve(corePort);
      } catch (error) {
        const errorMessage = error instanceof Error ? error.message : String(error);
        reject(new Error(`Failed to run Cline Core service: ${errorMessage}`));
      }
    });
  });
}

async function startClineCoreService(corePort: number, hostbridgePort: number) {
  const PLATFORM_NAME = getPlatformName();
  const BINARY_MODULES_DIR = path.join(INSTALL_DIR, 'binaries', PLATFORM_NAME, 'node_modules');
  const nodePath = [BINARY_MODULES_DIR, path.join(INSTALL_DIR, 'node_modules')].join(path.delimiter);

  const env = {
    ...process.env,
    NODE_PATH: nodePath,
    DEV_WORKSPACE_FOLDER: '/tmp/',
    PROTOBUS_ADDRESS: `127.0.0.1:${corePort}`,
    HOST_BRIDGE_ADDRESS: `127.0.0.1:${hostbridgePort}`
  };

  return spawn('node', [
    'cline-core.js'
  ], {
    cwd: INSTALL_DIR,
    env: env,
    stdio: 'ignore',
  })
}

export async function performCleanup() {
  if (isShuttingDown) return;
  isShuttingDown = true;

  hostBridgeServer.forceShutdown();

  const waitForServiceExit = (child: ChildProcess): Promise<number> => {
    return new Promise((resolve) => {
      child.on('exit', (code) => {
        resolve(code === null ? 1 : code);
      });
      child.on('error', () => resolve(1));
    });
  };

  if (clineCoreChild && !clineCoreChild.killed) {
    clineCoreChild.removeAllListeners('exit');
    clineCoreChild.kill('SIGTERM');
    await waitForServiceExit(clineCoreChild);
  }
}

export async function exitWithCode(code: number) {
  await performCleanup()
  process.exit(code)
}