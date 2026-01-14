import * as grpc from "@grpc/grpc-js";
import { host } from "../support";
import { stateMachine } from "./state";
import * as fs from 'fs';
import * as path from 'path';

function checkFileExists(filePath: string | undefined): boolean {
  if (filePath === undefined) {
    return false;
  }
  const absoluteFilePath = path.resolve(filePath);
  return fs.existsSync(absoluteFilePath)
}

function checkDiffIdExists(diffId: string | undefined): boolean {
  return diffId !== undefined && stateMachine.openedDiffs.has(diffId)
}

export class DiffServiceImpl implements host.DiffServiceServer {
  [name: string]: grpc.UntypedHandleCall;

  /** Open the diff view/editor. */
  openDiff: grpc.handleUnaryCall<host.OpenDiffRequest, host.OpenDiffResponse> = (call, callback) => {
    const filePath = call.request.path

    if (filePath === undefined) {
      console.error(`Required file path.`)
      return callback(new Error(`Required file path.`), null);
    }

    const diffId = "diff-" + Date.now()
    stateMachine.openedDiffs.set(diffId, filePath as string)
    const response = host.OpenDiffResponse.create({
      diffId: diffId
    });
    callback(null, response);
  };

  /** Get the contents of the diff view. */
  getDocumentText: grpc.handleUnaryCall<host.GetDocumentTextRequest, host.GetDocumentTextResponse> = (call, callback) => {
    const diffId = call.request.diffId

    if (!checkDiffIdExists(diffId)) {
      console.error(`Diff id not found or invalid: ${diffId}`)
      return callback(new Error(`Diff id not found or invalid: ${diffId}`), null);
    }
    const filePath = stateMachine.openedDiffs.get(diffId as string) as string;

    if (!checkFileExists(filePath)) {
      console.error(`File not exists: ${filePath}.`)
      return callback(new Error(`File not exists: ${filePath}`), null)
    }

    try {
      const content = fs.readFileSync(filePath, 'utf8');
      const response = host.GetDocumentTextResponse.create({
        content: content
      });
      callback(null, response);
    } catch (error) {
      const errorMessage = error instanceof Error
        ? error.message
        : `Failed to read file: ${filePath}`;
      console.error(`Failed to read file: ${errorMessage}`)
      callback(new Error(`Failed to read file: ${errorMessage}`), null);
    }
  };

  /** Replace a text selection in the diff. */
  replaceText: grpc.handleUnaryCall<host.ReplaceTextRequest, host.ReplaceTextResponse> = (call, callback) => {
    const { diffId, content, startLine, endLine } = call.request;

    if (
      content === undefined ||
      startLine === undefined ||
      endLine === undefined ||
      startLine < 0 ||
      endLine < startLine
    ) {
      return callback(new Error("Invalid request: content, startLine, and endLine are required and must be valid."), null);
    }

    if (!checkDiffIdExists(diffId)) {
      console.error(`Diff id not found or invalid: ${diffId}`)
      return callback(new Error(`Diff id not found or invalid: ${diffId}`), null);
    }

    const filePath = stateMachine.openedDiffs.get(diffId as string) as string;
    let lines: string[] = [];
    let fileExists = checkFileExists(filePath);

    try {
      if (fileExists) {
        const fileContent = fs.readFileSync(filePath, 'utf8');
        lines = fileContent.split('\n');
      }
      const lineCount = lines.length;

      if (startLine === lineCount && endLine === lineCount) {
        lines.push(content);
      } else {
        if (startLine === endLine) {
          lines[startLine] = content;
        } else {
          lines.splice(startLine, endLine - startLine + 1, content);
        }
      }

      const newContent = lines.join('\n');
      fs.writeFileSync(filePath, newContent, 'utf8');

      const response = host.ReplaceTextResponse.create();
      callback(null, response);

    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : `Failed to process file ${filePath}`;
      console.error(`Replace text failed: ${errorMessage}`)
      callback(new Error(`Replace text failed: ${errorMessage}`), null);
    }
  };

  scrollDiff: grpc.handleUnaryCall<host.ScrollDiffRequest, host.ScrollDiffResponse> = (call, callback) => {
    const response = host.ScrollDiffResponse.create();
    callback(null, response);
  };

  /** Truncate the diff document. */
  truncateDocument: grpc.handleUnaryCall<host.TruncateDocumentRequest, host.TruncateDocumentResponse> = (call, callback) => {
    const { diffId, endLine } = call.request;

    if (endLine === undefined || endLine < 0) {
      console.error("endLine is required and must be a non-negative number")
      return callback(new Error("endLine is required and must be a non-negative number"), null)
    }

    if (!checkDiffIdExists(diffId)) {
      console.error(`Diff id not found or invalid: ${diffId}`)
      return callback(new Error(`Diff id not found or invalid: ${diffId}`), null)
    }

    const filePath = stateMachine.openedDiffs.get(diffId as string) as string;

    if (!checkFileExists(filePath)) {
      console.error(`File not exists: ${filePath}.`)
      return callback(new Error(`File not exists: ${filePath}`), null)
    }

    try {
      const fileContent = fs.readFileSync(filePath, 'utf8');
      const lines = fileContent.split('\n');
      const lineCount = lines.length;

      if (endLine >= lineCount) {
        const response = host.TruncateDocumentResponse.create();
        return callback(null, response)
      }

      const truncatedLines = lines.slice(0, endLine);
      const newContent = truncatedLines.join('\n');

      fs.writeFileSync(filePath, newContent, 'utf8');

      const response = host.TruncateDocumentResponse.create();
      callback(null, response);
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : `Failed to truncate document: ${filePath}`;
      console.error(`Error truncating document: ${errorMessage}`);
      callback(new Error(`Truncate document failed: ${errorMessage}`), null);
    }
  };

  /** Save the diff document. */
  saveDocument: grpc.handleUnaryCall<host.SaveDocumentRequest, host.SaveDocumentResponse> = (call, callback) => {
    const response = host.SaveDocumentResponse.create();
    callback(null, response);
  };

  /**
   * Close all the diff editor windows/tabs.
   * Any diff editors with unsaved content should not be closed.
   */
  closeAllDiffs: grpc.handleUnaryCall<host.CloseAllDiffsRequest, host.CloseAllDiffsResponse> = (call, callback) => {
    stateMachine.openedDiffs.clear()

    const response = host.CloseAllDiffsResponse.create();
    callback(null, response);
  };

  /**
   * Display a diff view comparing before/after states for multiple files.
   * Content is passed as in-memory data, not read from the file system.
   */
  openMultiFileDiff: grpc.handleUnaryCall<host.OpenMultiFileDiffRequest, host.OpenMultiFileDiffResponse> = (call, callback) => {
    const response = host.OpenMultiFileDiffResponse.create();
    callback(null, response);
  };
}