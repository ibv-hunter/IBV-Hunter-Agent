import * as grpc from "@grpc/grpc-js"
import { host } from "../support";
import { stateMachine } from "./state"
import * as os from 'os';
import * as path from 'path';
import { randomUUID } from 'crypto';

export class WindowServiceImpl implements host.WindowServiceServer {
  [name: string]: grpc.UntypedHandleCall;

  /** Opens a text document in the IDE editor and returns editor information. */
  showTextDocument: grpc.handleUnaryCall<host.ShowTextDocumentRequest, host.TextEditorInfo> = (call, callback) => {
    const response = host.TextEditorInfo.create({
      documentPath: call.request?.path || "",
      viewColumn: 1,
      isActive: true,
    });
    callback(null, response);
  };

  /** Shows the open file dialogue / file picker. */
  showOpenDialogue: grpc.handleUnaryCall<host.ShowOpenDialogueRequest, host.SelectedResources> = (call, callback) => {
    const response = host.SelectedResources.create({
      paths: []
    });
    callback(new Error("Cannot show open dialogue: Standalone mode doesn't support user interaction."), response);
  };

  /** Shows a notification. */
  showMessage: grpc.handleUnaryCall<host.ShowMessageRequest, host.SelectedResponse> = (call, callback) => {
    let selected = undefined
    if (call.request.options) {
      // Always select the first option.
      selected = call.request.options.items[0]
    }
    const response = host.SelectedResponse.create({
      selectedOption: selected
    });
    callback(null, response);
  };

  /** Prompts the user for input and returns the response. */
  showInputBox: grpc.handleUnaryCall<host.ShowInputBoxRequest, host.ShowInputBoxResponse> = (call, callback) => {
    const response = host.ShowInputBoxResponse.create({
      response: "Cannot show input box: Standalone mode doesn't support user interaction."
    });
    callback(null, response);
  };

  /** Shows the file save dialogue / file picker. */
  showSaveDialog: grpc.handleUnaryCall<host.ShowSaveDialogRequest, host.ShowSaveDialogResponse> = (call, callback) => {
    const response = host.ShowSaveDialogResponse.create({
      selectedPath: path.join(os.tmpdir(), `${randomUUID()}.txt`)
    });
    callback(null, response);
  };

  /** Opens a file in the IDE. */
  openFile: grpc.handleUnaryCall<host.OpenFileRequest, host.OpenFileResponse> = (call, callback) => {
    const response = host.OpenFileResponse.create();
    callback(null, response);
  };

  /** Opens the host settings UI, optionally focusing a specific query/section. */
  openSettings: grpc.handleUnaryCall<host.OpenSettingsRequest, host.OpenSettingsResponse> = (call, callback) => {
    const response = host.OpenSettingsResponse.create();
    callback(null, response);
  };

  /** Returns the open tabs. */
  getOpenTabs: grpc.handleUnaryCall<host.GetOpenTabsRequest, host.GetOpenTabsResponse> = (call, callback) => {
    const response = host.GetOpenTabsResponse.create({
      paths: stateMachine.openedTabs
    });
    callback(null, response);
  };

  /** Returns the visible tabs. */
  getVisibleTabs: grpc.handleUnaryCall<host.GetVisibleTabsRequest, host.GetVisibleTabsResponse> = (call, callback) => {
    const response = host.GetVisibleTabsResponse.create({
      paths: stateMachine.visibleTabs
    });
    callback(null, response);
  };

  /** Returns information about the current editor */
  getActiveEditor: grpc.handleUnaryCall<host.GetActiveEditorRequest, host.GetActiveEditorResponse> = (call, callback) => {
    const response = host.GetActiveEditorResponse.create({
      filePath: ""
    });
    callback(null, response);
  };
}