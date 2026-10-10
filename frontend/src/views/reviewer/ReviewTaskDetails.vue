<!-- V02: Collect an independent decision and lock the submitted review. -->
<script>
import { useWorkspace } from "../../state/workspace.js";
export default { setup: useWorkspace };
</script>

<template>
  <div class="review-grid">
    <section class="panel">
      <div class="panel-title">
        <h2>Professional profile</h2>
        <span>{{ task.case }} · {{ task.name }}</span>
      </div>
      <div class="panel-body">
        <div class="callout">
          Blind review · Other human and automated decisions are hidden.
        </div>
        <section
          class="evidence"
          v-for="[k, v] in [
            ['Skills', 'Java · SQL · Git'],
            ['Experience', 'Backend development · 2 reviewed years'],
            ['Education', 'Bachelor · Computer Science'],
            ['Languages', 'English · B2 (self-reported)'],
            ['Projects', 'Database API · Java and SQL'],
          ]"
        >
          <h3>{{ k }}</h3>
          <p>{{ v }}</p>
          <blockquote>
            Synthetic professional evidence for interface demonstration.
          </blockquote>
        </section>
      </div>
    </section>
    <section class="panel decision-panel">
      <div class="panel-title">
        <h2>Your decision</h2>
        <span class="badge">{{ task.mode }}</span>
      </div>
      <div class="panel-body">
        <div class="callout" v-if="task.mode === 'Instructed-Bias'">
          Only apply the task's authorized instruction. Production instructions
          are not connected in this preview.
        </div>
        <template v-if="task.status === 'Submitted'"
          ><span class="badge completed">Submitted · Locked</span>
          <h3>{{ task.decision }}</h3>
          <p>{{ task.reason }}</p>
          <p class="muted">Confirmed submissions cannot be edited.</p></template
        >
        <form v-else @submit.prevent="submit">
          <div class="decision-options">
            <label v-for="d in ['Accept', 'Reject']"
              ><input type="radio" v-model="decision" :value="d" required />{{
                d
              }}</label
            >
          </div>
          <label
            >Reason <em>*</em
            ><textarea
              v-model="reason"
              maxlength="2000"
              rows="7"
              required
              placeholder="Explain your decision using professional evidence."
            ></textarea></label
          ><small>{{ reason.trim().length }} / 2000 characters</small
          ><label class="check"
            ><input type="checkbox" v-model="confirm" required />I confirm my
            independent decision. Submission locks this review.</label
          >
          <p class="error" v-if="error">{{ error }}</p>
          <button class="primary wide">Submit review</button>
        </form>
      </div>
    </section>
  </div>
</template>
