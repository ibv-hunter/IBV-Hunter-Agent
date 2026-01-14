import * as grpc from "@grpc/grpc-js"
import { host, cline } from "../support";
import * as os from "os"
import { execSync } from "child_process";

function executeCommand(command: string): string {
  try {
    return execSync(command, { encoding: 'utf8', stdio: ['pipe', 'pipe', 'ignore'] }).trim();
  } catch (error) {
    throw new Error(`Command execution failed: ${(error as Error).message}`);
  }
}

export class EnvServiceImpl implements host.EnvServiceServer {
  [name: string]: grpc.UntypedHandleCall;

  /** Writes text to the system clipboard. */
  clipboardWriteText: grpc.handleUnaryCall<cline.StringRequest, cline.Empty> = (call, callback) => {
    const text = call.request.value;

    if (text === undefined) {
      return callback(new Error("Text content is required"), null);
    }

    try {
      let command: string;
      const platform = os.platform();

      switch (platform) {
        case 'win32':
          command = `echo ${JSON.stringify(text)} | clip`;
          break;
        case 'darwin':
          command = `echo ${JSON.stringify(text)} | pbcopy`;
          break;
        case 'linux':
          command = `echo ${JSON.stringify(text)} | xclip -selection clipboard`;
          break;
        default:
          throw new Error(`Unsupported platform: ${platform}`);
      }

      executeCommand(command);

      const response = cline.Empty.create();
      callback(null, response);
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : "Failed to write to clipboard";
      console.error(`Clipboard write error: ${errorMessage}`);
      callback(new Error(`Write to clipboard failed: ${errorMessage}`), null);
    }
  };

  /** Reads text from the system clipboard. */
  clipboardReadText: grpc.handleUnaryCall<cline.EmptyRequest, cline.String> = (call, callback) => {
    try {
      let command: string;
      const platform = os.platform();

      switch (platform) {
        case 'win32':
          command = 'powershell -command "Get-Clipboard"';
          break;
        case 'darwin':
          command = 'pbpaste';
          break;
        case 'linux':
          command = 'xclip -selection clipboard -o';
          break;
        default:
          throw new Error(`Unsupported platform: ${platform}`);
      }

      const text = executeCommand(command);
      const response = cline.String.create({
        value: text
      });
      callback(null, response);
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : "Failed to read from clipboard";
      console.error(`Clipboard read error: ${errorMessage}`);
      callback(new Error(`Read from clipboard failed: ${errorMessage}`), null);
    }
  };

  /** Returns a stable machine identifier for telemetry distinctId purposes. */
  getMachineId: grpc.handleUnaryCall<cline.EmptyRequest, cline.String> = (call, callback) => {
    const response = cline.String.create({
      value: "machine-id-" + os.hostname()
    });
    callback(null, response);
  };

  /** Returns the name and version of the host IDE or environment. */
  getHostVersion: grpc.handleUnaryCall<cline.EmptyRequest, host.GetHostVersionResponse> = (call, callback) => {
    const response = host.GetHostVersionResponse.create({
      platform: "Standalone-HostBridge",
      version: "1.0",
      clineType: "CLI",
      clineVersion: "1.0"
    });
    callback(null, response);
  };

  /**
   * Returns a URI that will redirect to the host environment.
   * e.g. vscode://saoudrizwan.claude-dev, idea://, pycharm://, etc.
   * If the host does not support URIs it should return empty.
   */
  getIdeRedirectUri: grpc.handleUnaryCall<cline.EmptyRequest, cline.String> = (call, callback) => {
    const response = cline.String.create({
      value: ""
    });
    callback(null, response);
  };

  /**
   * Returns the telemetry settings of the host environment. This may return UNSUPPORTED
   * if the host does not specify telemetry settings for the plugin.
   */
  getTelemetrySettings: grpc.handleUnaryCall<cline.EmptyRequest, host.GetTelemetrySettingsResponse> = (call, callback) => {
    const response = host.GetTelemetrySettingsResponse.create({
      isEnabled: host.Setting.UNSUPPORTED
    });
    callback(null, response);
  };

  /** Returns events when the telemetry settings change. */
  subscribeToTelemetrySettings(
    stream: grpc.ServerWritableStream<cline.EmptyRequest, host.TelemetrySettingsEvent>
  ): void {
    stream.end();
  }

  shutdown: grpc.handleUnaryCall<cline.EmptyRequest, cline.Empty> = (call, callback) => {
    const response = cline.Empty.create();
    callback(null, response);
    process.exit(0)
  };
}