class StateMachine {
  workspaceDir: string[] = [ process.env.CLINE_WORKSPACE_DIR || process.cwd() ];
  openedDiffs: Map<string, string> = new Map();
  openedTabs: string[] = []
  visibleTabs: string[] = []
}

export const stateMachine = new StateMachine();