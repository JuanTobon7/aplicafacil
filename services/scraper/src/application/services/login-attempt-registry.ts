import { LoginAttempt } from "./login-attempt";
import { LoginErrors } from "../exception/login-errors";

/** Hay una sola ventana visible: un intento de login a la vez en todo el proceso. */
export class LoginAttemptRegistry {
  private active?: LoginAttempt;

  acquire(attemptId: string): LoginAttempt {
    if (this.active) throw LoginErrors.alreadyConnecting();
    this.active = new LoginAttempt(attemptId);
    return this.active;
  }

  find(attemptId: string): LoginAttempt | undefined {
    return this.active?.id === attemptId ? this.active : undefined;
  }

  cancel(attemptId: string) {
    this.find(attemptId)?.cancel();
  }

  async release(attempt: LoginAttempt) {
    await attempt.dispose();
    if (this.active === attempt) this.active = undefined;
  }
}
