#!/usr/bin/env npx tsx
import { cline } from "./support";
import { CheckpointsService, ModelsService, StateService, TaskService, UiService } from "./cline-core-api";
import { ClineMessage } from "./cline-core-api";
import { exitWithCode, isShuttingDown } from "./cline-core-service";

const messages: ClineMessage[] = [];
const checkpoints: number[] = [];

let followUpTimes = 0
let mistakeLimitReachedTimes = 0
let apiReqFailedTimes = 0

const ASK_REPLY_DELAY_MS = 3000
const COMPLETION_DELAY_MS = 1000;
const INITIAL_RETRY_DELAY_MS = 1000;

const MAX_RETRIES = 5;
const MAX_MISTAKE_LIMIT_REACHED_TIMES = 10
const MAX_FOLLOWUP_TIMES = 10
const MAX_API_REQ_FAILED_TIMES = 10

const delay = (ms: number) => new Promise(resolve => setTimeout(resolve, ms));

const defaultFollowUpReply = `You are currently performing an automated task and cannot ask the user for input.  The system is searching for the necessary answer based on your question.
1. If you believe the task requires missing parameters, please note that all task parameters are currently complete. Please go back to the previous step and try again;
2. If you encounter network or API call errors, please follow the original prompt instructions;
3. If you don't know what to do next, it is recommended that you review the contents of the to-do list. If the current step cannot proceed, go back to the previous step and consider if you made a mistake;
4. Note: If the number of questions exceeds ${MAX_FOLLOWUP_TIMES}, the task will be terminated.
`

function isAsk(message: ClineMessage) {
  return message.type === "ask"
}

function isClineAsk(message: cline.ClineMessage) {
  return message.type === cline.ClineMessageType.ASK
}

function isSay(message: ClineMessage) {
  return message.type === "say"
}

function isClineSay(message: cline.ClineMessage) {
  return message.type === cline.ClineMessageType.SAY
}

function replyAskWithYes(delay: number = ASK_REPLY_DELAY_MS) {
  setTimeout(() => {
    TaskService.askResponse("yesButtonClicked", "Allow opeartion")
  }, delay)
}

function replyAskWithNo(delay: number = ASK_REPLY_DELAY_MS) {
  setTimeout(() => {
    TaskService.askResponse("noButtonClicked", "Access denied. You need to consider the appropriateness of your current actions and avoid repeatedly retrying the operation.")
  }, delay)
}

function replyAskWithText(text: string, delay: number = ASK_REPLY_DELAY_MS) {
  setTimeout(() => {
    TaskService.askResponse("messageResponse", text)
  }, delay)
}

function isProcessedMessage(msg1: ClineMessage, msg2: ClineMessage) {
  return msg1.ts === msg2.ts
}

async function processFollowUpAsk(message: ClineMessage) {
  followUpTimes++
  if (followUpTimes >= MAX_FOLLOWUP_TIMES) {
    console.error(`🚨 The maximum number of follow-up questions (${MAX_FOLLOWUP_TIMES}) has been reached; preparing to exit.`)
    await exitWithCode(1)
    return
  }
  console.log(`⚠️ The model generated a question. This run has generated ${followUpTimes} questions so far, using the default answer: ${defaultFollowUpReply}`)
  replyAskWithText(defaultFollowUpReply)
}

async function processMistake() {
  mistakeLimitReachedTimes++
  if (mistakeLimitReachedTimes >= MAX_MISTAKE_LIMIT_REACHED_TIMES) {
    console.error(`🚨 The maximum number of errors (${MAX_MISTAKE_LIMIT_REACHED_TIMES}) has been reached, preparing to exit.`)
    await exitWithCode(1)
    return
  }
  console.log(`⚠️ The model encountered an error; this run has generated ${mistakeLimitReachedTimes} errors so far.`)
  replyAskWithYes()
}

async function processApiReqFailed() {
  apiReqFailedTimes++
  if (apiReqFailedTimes >= MAX_API_REQ_FAILED_TIMES) {
    console.error(`🚨 The maximum number of error attempts (${MAX_API_REQ_FAILED_TIMES}) has been reached; preparing to exit.`)
    await exitWithCode(1)
    return
  }
  console.log(`⚠️ API request retry failed. This run has resulted in ${apiReqFailedTimes} failed request retries.`)
  replyAskWithYes()
}

function printSayMessage(message: ClineMessage) {
  if (message.partial === true) {
    return
  }
  const datetime = new Date(message.ts)
  console.log(`${datetime.toLocaleString()}\tSAY\t[${message.say}] ${message.text || message.reasoning || ""}`)
}

function printClineSayMessage(message: cline.ClineMessage) {
  if (message.partial) {
    return
  }
  const datetime = new Date(message.ts)
  const sayString = cline.clineSayToJSON(message.say).toLowerCase()
  console.log(`${datetime.toLocaleString()}\tSAY\t[${sayString}] ${message.text || message.reasoning || ""}`)
}

async function restoreToLastSuccess(currentTime: number) {
  if (checkpoints.length === 0) {
    console.error(`❌ An error occurred and it was not possible to roll back because no checkpoints were saved.`)
    return
  }

  let lastCheckpoint: number = 0

  while (checkpoints.length > 0) {
    const checkpoint = checkpoints.pop()
    if (checkpoint === undefined) {
      break
    }
    if (checkpoint < currentTime) {
      lastCheckpoint = checkpoint
      break
    }
  }

  if (lastCheckpoint != 0) {
    const datetime = new Date(lastCheckpoint)
    console.log(`⚠️ An error occurred. Attempting to roll back to checkpoint ${lastCheckpoint}, Time: ${datetime.toLocaleString()}.`)
    await CheckpointsService.checkpointRestore(lastCheckpoint).catch((error) => {
      const errorMessage = error instanceof Error ? error.message : String(error);
      console.error(`❌ Rolling back to checkpoint time ${datetime.toLocaleString()} failed. Error message: ${errorMessage}.`)
    })
  } else {
    console.log(`❌ An error occurred, and there is no checkpoint available to roll back to.`)
  }
}

async function processSay(taskId: string, clineMessage: ClineMessage) {
  switch (clineMessage.say) {
    case "text":
    case "info":
      if (clineMessage.text != null) {
        printSayMessage(clineMessage)
      }
      break
    case "mcp_server_response":
      if (clineMessage.text != null) {
        clineMessage.text = clineMessage.text.replace(/[\r\n\f\v]/g, '\\n');
        printSayMessage(clineMessage)
      }
      break
    case "checkpoint_created":
      checkpoints.push(clineMessage.ts)
      break
    case "error":
      printSayMessage(clineMessage)
      break
    case "api_req_started":
    case "api_req_retried":
    case "api_req_finished":
    case "deleted_api_reqs":
    case "mcp_server_request_started":
    case "browser_action_result":
      break
    default:
      printSayMessage(clineMessage)
      break
  }
}

async function processClineSay(taskId: string, clineMessage: cline.ClineMessage) {
  switch (clineMessage.say) {
    case cline.ClineSay.TEXT:
    case cline.ClineSay.COMPLETION_RESULT_SAY:
    case cline.ClineSay.TOOL_SAY:
    case cline.ClineSay.COMMAND_SAY:
    case cline.ClineSay.USE_MCP_SERVER_SAY:
      printClineSayMessage(clineMessage)
      break
    default:
      break
  }
}

async function processAsk(taskId: string, clineMessage: ClineMessage) {
  switch (clineMessage.ask) {
    case "tool":
    case "command":
    case "use_mcp_server":
    case "browser_action_launch":
      // Whether using tools, using MCP, executing commands, or using a browser, direct consent is given here.
      replyAskWithYes()
      break
    case "command_output":
      // Command output
      break
    case "followup":
      // The AI generates questions, and this requires careful handling.
      await processFollowUpAsk(clineMessage)
      break
    case "api_req_failed":
      // API request failed, retrying immediately.
      await processApiReqFailed()
      break
    case "mistake_limit_reached":
      // An internal model error has occurred; the program needs to exit.
      await processMistake()
      break
    case "completion_result":
    case "resume_completed_task":
      // Task completed, exiting the program.
      console.log(`🍺 We have received the message "[${clineMessage.ask}]" and are preparing to exit...`)
      await delay(COMPLETION_DELAY_MS)
      await exitWithCode(0)
      break
    default:
      // The `replyAskWithNoFallback` function prevents unexpected execution by not providing a default or fallback option.
      replyAskWithNo()
      break
  }
}

async function processState(taskId: string, stateJson: string) {
  const parsed = JSON.parse(stateJson)
  for (const msg of parsed.clineMessages) {
    const clineMessage = msg as ClineMessage

    const isProcessed = messages.some(
      msg => isProcessedMessage(msg, clineMessage)
    );

    if (!isProcessed) {
      messages.push(clineMessage)

      if (isAsk(clineMessage)) {
        await processAsk(taskId, clineMessage)
      }

      if (isSay(clineMessage)) {
        await processSay(taskId, clineMessage)
      }
    }
  }
}

function processPartialMessage(taskId: string, clineMessage: cline.ClineMessage) {
  if (isClineSay(clineMessage)) {
    processClineSay(taskId, clineMessage)
  }
}

async function startNewTask(prompt: string) {
  await StateService.togglePlanActModeProto(cline.PlanActMode.ACT);
  await ModelsService.updateApiConfigurationProto();
  return await TaskService.newTask(prompt);
}

async function subscribeStateWithRetry(taskId: string, attempt: number = 0) {
  if (attempt >= MAX_RETRIES) {
    console.error(`🚨 The subscription to the state has reached the maximum number of retries (${MAX_RETRIES}), giving up.`);
    await exitWithCode(1)
    return
  }

  // 状态消息订阅
  StateService.subscribeToState((stateJson, error) => {
    if (stateJson) {
      processState(taskId, stateJson)
    }
    if (error && !isShuttingDown) {
      console.warn(`⚠️ subscribeToState stream error: ${error.message}. Preparing to retry...`);

      delay(INITIAL_RETRY_DELAY_MS * Math.pow(2, attempt))
        .then(() => subscribeStateWithRetry(taskId, attempt + 1));

      return;
    }
  })
}

async function subscribePartialMessageWithRetry(taskId: string, attempt: number = 0) {
  if (attempt >= MAX_RETRIES) {
    console.error(`🚨 The subscription for partial messages has reached the maximum number of retries (${MAX_RETRIES}), giving up.`);
    await exitWithCode(1)
    return
  }

  // Partial消息订阅
  UiService.subscribeToPartialMessage((clineMessage, error) => {
    if (clineMessage) {
      processPartialMessage(taskId, clineMessage)
    }
    if (error && !isShuttingDown) {
      console.warn(`⚠️ subscribeToState stream error: ${error.message}. Preparing to retry...`);

      delay(INITIAL_RETRY_DELAY_MS * Math.pow(2, attempt))
        .then(() => subscribePartialMessageWithRetry(taskId, attempt + 1));

      return;
    }
  })
}

async function exec(argv: string[]) {
  const args = argv.slice(2);

  if (args.length < 1) {
    console.error('Error: Invalid number of arguments.');
    console.log('Usage: node cline-exec.js <command> [args]');
    await exitWithCode(1)
    return
  }

  const cmd = args[0]
  switch (cmd) {
    case "task":
      if (args.length < 2) {
        console.error('Error: Invalid number of arguments.');
        console.log('Usage: node cline-exec.js task <prompt>');
        process.exit(1);
      }
      const prompt = args[1]

      // You must wait for the `newTask` request to complete before subscribing; otherwise, it will print the message from the previous task.
      const taskId = await startNewTask(prompt).catch(async (error) => {
        const errorMessage = error instanceof Error ? error.message : String(error);
        console.error(`Task creation failed, error message: ${errorMessage}.`)
        await exitWithCode(1)
        return
      })

      console.log("Task created: " + taskId)

      subscribeStateWithRetry(taskId!)
      subscribePartialMessageWithRetry(taskId!)

      break
    default:
      console.error(`Error: Unknown command "${cmd}".`);
      console.log('Usage: node cline-exec.js <command> [args]');
      await exitWithCode(1)
      return
  }
}

async function main() {
  process.stdout.isTTY = true
  await exec(process.argv).catch(async (err) => {
    console.error("🚨 Failed to execute cline-exec: ", err)
    await exitWithCode(1)
  })
}

main()
