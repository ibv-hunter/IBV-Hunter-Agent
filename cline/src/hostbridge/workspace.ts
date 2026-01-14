import * as grpc from "@grpc/grpc-js"
import { host } from "../support";
import { stateMachine } from "./state"

export class WorkspaceServiceImpl implements host.WorkspaceServiceServer {
  [name: string]: grpc.UntypedHandleCall;

  /** Returns a list of the top level directories of the workspace. */
  getWorkspacePaths: grpc.handleUnaryCall<host.GetWorkspacePathsRequest, host.GetWorkspacePathsResponse> = (call, callback) => {
    callback(null, host.GetWorkspacePathsResponse.create({
      paths: stateMachine.workspaceDir
    }));
  };

  /**
   * Saves an open document if it's open in the editor and has unsaved changes.
   * Returns true if the document was saved, returns false if the document was not found, or did not
   * need to be saved.
   */
  saveOpenDocumentIfDirty: grpc.handleUnaryCall<host.SaveOpenDocumentIfDirtyRequest, host.SaveOpenDocumentIfDirtyResponse> = (call, callback) => {
    const response = host.SaveOpenDocumentIfDirtyResponse.create({
      wasSaved: true
    });
    callback(null, response);
  };

  /** Get diagnostics from the workspace. */
  getDiagnostics: grpc.handleUnaryCall<host.GetDiagnosticsRequest, host.GetDiagnosticsResponse> = (call, callback) => {
    const response = host.GetDiagnosticsResponse.create({
      fileDiagnostics: []
    });
    callback(null, response);
  };

  /** Makes the problems panel/pane visible in the IDE and focuses it. */
  openProblemsPanel: grpc.handleUnaryCall<host.OpenProblemsPanelRequest, host.OpenProblemsPanelResponse> = (call, callback) => {
    const response = host.OpenProblemsPanelResponse.create();
    callback(null, response);
  };

  /** Opens the IDE file explorer panel and selects a file or directory. */
  openInFileExplorerPanel: grpc.handleUnaryCall<host.OpenInFileExplorerPanelRequest, host.OpenInFileExplorerPanelResponse> = (call, callback) => {
    const response = host.OpenInFileExplorerPanelResponse.create();
    callback(null, response);
  };

  /** Opens and focuses the Cline sidebar panel in the host IDE. */
  openClineSidebarPanel: grpc.handleUnaryCall<host.OpenClineSidebarPanelRequest, host.OpenClineSidebarPanelResponse> = (call, callback) => {
    const response = host.OpenClineSidebarPanelResponse.create();
    callback(null, response);
  };

  /** Opens and focuses the terminal panel. */
  openTerminalPanel: grpc.handleUnaryCall<host.OpenTerminalRequest, host.OpenTerminalResponse> = (call, callback) => {
    const response = host.OpenTerminalResponse.create();
    callback(null, response);
  };

  /** Executes a command in a new terminal */
  executeCommandInTerminal: grpc.handleUnaryCall<host.ExecuteCommandInTerminalRequest, host.ExecuteCommandInTerminalResponse> = (call, callback) => {
    const response = host.ExecuteCommandInTerminalResponse.create();
    callback(null, response);
  };
}
