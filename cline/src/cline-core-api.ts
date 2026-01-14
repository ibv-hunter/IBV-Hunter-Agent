import {GrpcAdapter} from './grpcAdapter'
import {cline} from './support';
import {performCleanup, startCline} from './cline-core-service'
import * as config from "./model-config/config"
import * as secret from "./model-config/secret"
import {ClientReadableStream} from "@grpc/grpc-js";

const client: GrpcAdapter = await createClineClient()

const emptyReq = {
  message: cline.EmptyRequest.create({})
};

async function createClineClient(): Promise<GrpcAdapter>{
  try {
    const corePort = await startCline();
    return new GrpcAdapter(`127.0.0.1:${corePort}`);
  } catch (error) {
    const errorMessage = error instanceof Error ? error.message : String(error);
    console.log(`❌ Core Service failed to start, error message: ${errorMessage}, the process will exit.`);
    await performCleanup()
    process.exit(1)
  }
}

export class ModelsService {
  static async updateApiConfigurationProto(modelId: string = config.API_MODEL_ID) {

    const request = {
      message: cline.UpdateApiConfigurationRequest.create({
        apiConfiguration: cline.ModelsApiConfiguration.create({
          /** Global configuration fields (not mode-specific) */
          /** OpenAI compat configurations */
          // openAiApiKey: secret.OPENAI_API_KEY,
          // openAiBaseUrl: config.OPENAI_BASE_URL,

          /** Gemini configurations */
          geminiApiKey: secret.GEMINI_API_KEY,

          /** Act mode configurations */
          actModeApiProvider: cline.apiProviderFromJSON(config.API_PROVIDER),
          actModeOpenAiModelId: modelId,

          /** Plan mode configurations */
          planModeApiProvider: cline.apiProviderFromJSON(config.API_PROVIDER),
          planModeOpenAiModelId: modelId,
        })
      })
    };
    await client.call("cline.ModelsService", "updateApiConfigurationProto", request)
  }
}

export type StateCallback = (stateJson?: string, error?: any) => void;
export type PartialMessageCallback = (clineMessage?: cline.ClineMessage, error?: any) => void;
export type CheckpointCallback = (event: cline.CheckpointEvent, error?: any) => void;

export interface ClineMessage {
  ts: number
  type: "ask" | "say"
  ask?: ClineAsk
  say?: ClineSay
  text?: string
  reasoning?: string
  images?: string[]
  files?: string[]
  partial?: boolean
  lastCheckpointHash?: string
  isCheckpointCheckedOut?: boolean
  isOperationOutsideWorkspace?: boolean
  conversationHistoryIndex?: number
  conversationHistoryDeletedRange?: [number, number] // for when conversation history is truncated for API requests
}

export type ClineAsk =
  | "followup"
  | "plan_mode_respond"
  | "command"
  | "command_output"
  | "completion_result"
  | "tool"
  | "api_req_failed"
  | "resume_task"
  | "resume_completed_task"
  | "mistake_limit_reached"
  | "auto_approval_max_req_reached"
  | "browser_action_launch"
  | "use_mcp_server"
  | "new_task"
  | "condense"
  | "summarize_task"
  | "report_bug"

export type ClineSay =
  | "task"
  | "error"
  | "api_req_started"
  | "api_req_finished"
  | "text"
  | "reasoning"
  | "completion_result"
  | "user_feedback"
  | "user_feedback_diff"
  | "api_req_retried"
  | "command"
  | "command_output"
  | "tool"
  | "shell_integration_warning"
  | "browser_action_launch"
  | "browser_action"
  | "browser_action_result"
  | "mcp_server_request_started"
  | "mcp_server_response"
  | "mcp_notification"
  | "use_mcp_server"
  | "diff_error"
  | "deleted_api_reqs"
  | "clineignore_error"
  | "checkpoint_created"
  | "load_mcp_documentation"
  | "info" // Added for general informational messages like retry status
  | "task_progress"

export class StateService {
  static async setWelcomeViewCompleted() {
    const request = {
      message: cline.BooleanRequest.create({
        value: true
      })
    };
    await client.call("cline.StateService", "setWelcomeViewCompleted", request)
  }

  static async togglePlanActModeProto(mode: number): Promise<boolean> {
    const request = {
      message: cline.TogglePlanActModeRequest.create({
        "mode": mode,
        "chatContent": {
          "images": [],
          "files": []
        }
      })
    };
    const response: cline.Boolean = await client.call(
      "cline.StateService", "togglePlanActModeProto", request)
    return response.value
  }

  static async getLatestState(): Promise<string> {
    const response: cline.State = await client.call(
      "cline.StateService", "getLatestState", emptyReq);
    return response.stateJson
  }

  static async subscribeToState(callback: StateCallback) {
    const stream: ClientReadableStream<cline.State> = client.stream_call(
      "cline.StateService", "subscribeToState", emptyReq);
    stream.on('data', (message: cline.State) => {
      callback(message.stateJson)
    });
    stream.on('error', (error: any) => {
      callback(undefined, error);
    });
  }
}

export class TaskService {
  static async newTask(prompt: string): Promise<string> {
    const request = {
      message: cline.NewTaskRequest.create({
        metadata: undefined,
        text: prompt,
        images: [],
        files: [],
        taskSettings: cline.Settings.create({
          autoApprovalSettings: cline.AutoApprovalSettings.create({
            version: 1,
            actions: cline.AutoApprovalActions.create({
              readFiles: true,
              readFilesExternally: true,
              editFiles: true,
              editFilesExternally: true,
              executeSafeCommands: true,
              executeAllCommands: false,
              useBrowser: false,
              useMcp: true
            }),
            enableNotifications: false,
          })
        })
      })
    };
    const result = await client.call("cline.TaskService", "newTask", request)
    return result.value
  }

  static async showTaskWithId(taskId: string): Promise<cline.TaskResponse> {
    const request = {
      message: cline.StringRequest.create({
        value: taskId
      })
    };
    return await client.call("cline.TaskService", "showTaskWithId", request)
  }

  static async cancelTask() {
    await client.call("cline.TaskService", "cancelTask", emptyReq)
  }

  static async askResponse(responseType: string, text: string) {
    const request = {
      message: cline.AskResponseRequest.create({
        metadata: undefined,
        responseType: responseType,
        text: text,
        images: [],
        files: []
      })
    };
    await client.call("cline.TaskService", "askResponse", request)
  }

  static async deleteTasksWithIds(taskIds: string[]) {
    const request = {
      message: cline.StringArrayRequest.create({
        value: taskIds
      })
    };
    await client.call("cline.TaskService", "deleteTasksWithIds", request)
  }

  static async deleteAllTaskHistory(): Promise<number> {
    const response: cline.DeleteAllTaskHistoryCount = await client.call(
      "cline.TaskService", "deleteAllTaskHistory", emptyReq);
    return response.tasksDeleted
  }
}

export class UiService {
  static async subscribeToPartialMessage(callback: PartialMessageCallback) {
    const stream: ClientReadableStream<cline.State> = client.stream_call(
      "cline.UiService", "subscribeToPartialMessage", emptyReq);
    stream.on('data', (message: cline.ClineMessage) => {
      callback(message)
    });
    stream.on('error', (error: any) => {
      callback(undefined, error);
    });
  }
}

export class CheckpointsService {
  static async checkpointRestore(checkpointID: number) {
    const request = {
      message: cline.CheckpointRestoreRequest.create({
        metadata: undefined,
        number: checkpointID,
        restoreType: "task"
      })
    };
    await client.call("cline.CheckpointsService", "checkpointRestore", request)
  }

  static async subscribeToCheckpoints(callback: CheckpointCallback) {
    const stream: ClientReadableStream<cline.State> = client.stream_call(
      "cline.CheckpointsService", "subscribeToCheckpoints", emptyReq);
    stream.on('data', (event: cline.CheckpointEvent) => {
      callback(event)
    });
    stream.on('error', (error: any) => {
      console.log(`subscribeToCheckpoints: ${error}.`)
    });
  }
}