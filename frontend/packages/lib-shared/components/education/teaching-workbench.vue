<template>
  <main class="edu-panel" :aria-busy="busy || loading">
    <h2>{{ t('edu.workbench') }}</h2
    ><p>{{ t('edu.workbenchHelp') }}</p> <p v-if="failed" role="alert">{{ t('edu.failed') }}</p
    ><p v-if="saved" role="status">{{ t('education.saved') }}</p>
    <p v-if="loading" role="status">{{ t('edu.loading') }}</p>
    <button type="button" :disabled="busy" @click="load">{{ t('edu.refresh') }}</button>
    <h3>{{ t('edu.queue') }}</h3
    ><p v-if="!queue.length && !loading">{{ t('edu.empty') }}</p>
    <div class="edu-grid"
      ><article v-for="item in queue" :key="item.sessionId" class="edu-card">
        <strong>{{ item.className }}</strong
        ><p>{{ date(item.startsAt) }}</p>
        <p
          >{{ t('edu.pendingAttendance') }} {{ item.pendingAttendance }} · {{ t('edu.pendingFeedback') }}
          {{ item.pendingFeedback }} · {{ t('edu.pendingSend') }} {{ item.pendingSend }}</p
        >
        <button type="button" @click="selectSession(item.sessionId)">{{ t('edu.openSession') }}</button>
      </article></div
    >
    <details v-if="canManage"
      ><summary>{{ t('edu.classAndSchedule') }}</summary>
      <form @submit.prevent="createClass"
        ><fieldset :disabled="busy"
          ><div class="edu-grid">
            <label>{{ t('edu.className') }}<input v-model="className" required maxlength="100" /></label>
            <label
              >{{ t('edu.mode')
              }}<select v-model="classMode"
                ><option value="HOURS">{{ t('edu.hours') }}</option
                ><option value="EVENT">{{ t('edu.event') }}</option></select
              ></label
            > </div
          ><button type="submit">{{ t('edu.createClass') }}</button></fieldset
        ></form
      >
      <p>{{ t('edu.scheduleHelp') }}</p>
      <form @submit.prevent="schedule"
        ><fieldset :disabled="busy"
          ><div class="edu-grid">
            <label
              >{{ t('edu.class')
              }}<select v-model="classId" required
                ><option value="">{{ t('edu.choose') }}</option
                ><option v-for="group in classes" :key="group.id" :value="group.id">{{ group.name }}</option></select
              ></label
            >
            <label
              >{{ t('edu.teacher')
              }}<select v-model="teacherId" required
                ><option value="">{{ t('edu.choose') }}</option
                ><option v-for="teacher in teachers" :key="teacher.id" :value="teacher.id">{{
                  teacher.name
                }}</option></select
              ></label
            >
            <label
              >{{ t('edu.room') }}<input v-model="roomKey" required pattern="[a-z0-9_-]{1,32}" maxlength="32"
            /></label>
            <label>{{ t('edu.start') }}<input v-model="startsAt" type="datetime-local" required /></label>
            <label>{{ t('edu.end') }}<input v-model="endsAt" type="datetime-local" required /></label>
            <label
              >{{ t('edu.makeupFrom')
              }}<select v-model="makeupFrom"
                ><option value="">{{ t('edu.regularSession') }}</option
                ><option
                  v-for="session in sessions.filter((s) => s.classId === classId && s.status === 'SCHEDULED')"
                  :key="session.id"
                  :value="session.id"
                  >{{ date(session.startsAt) }}</option
                ></select
              ></label
            > </div
          ><button type="submit">{{ t('edu.schedule') }}</button></fieldset
        ></form
      >
    </details>
    <h3>{{ t('edu.sessions') }}</h3>
    <label
      >{{ t('edu.session')
      }}<select v-model="selectedSession"
        ><option value="">{{ t('edu.choose') }}</option
        ><option v-for="session in sessions" :key="session.id" :value="session.id"
          >{{ date(session.startsAt) }} · {{ session.roomKey }} · {{ t(`edu.state.${session.status}`) }}</option
        ></select
      ></label
    >
    <template v-if="session">
      <button v-if="canManage && session.status === 'SCHEDULED'" type="button" :disabled="busy" @click="cancel">{{
        t('edu.cancelSession')
      }}</button>
      <p v-if="!participants.length">{{ t('edu.empty') }}</p>
      <div class="edu-grid"
        ><article v-for="learner in participants" :key="learner.enrollmentId" class="edu-card">
          <strong>{{ learner.studentName }}</strong
          ><p>{{ learner.courseTitle }}</p>
          <p
            >{{ t('edu.balance') }} {{ learner.balance }} ·
            {{ learner.attendanceStatus ? t(`edu.state.${learner.attendanceStatus}`) : t('edu.notRecorded') }}</p
          >
          <button type="button" @click="selectedEnrollment = learner.enrollmentId">{{
            t('edu.attendanceAndFeedback')
          }}</button>
        </article></div
      >
      <section v-if="participant" class="edu-card"
        ><h3>{{ participant.studentName }} · {{ participant.courseTitle }}</h3>
        <form @submit.prevent="record"
          ><fieldset :disabled="busy || session.status !== 'SCHEDULED' || session.startsAt > Date.now()">
            <label
              >{{ t('edu.attendance')
              }}<select v-model="attendanceStatus"
                ><option v-for="status in ['PRESENT', 'ABSENT', 'LEAVE']" :key="status" :value="status">{{
                  t(`edu.state.${status}`)
                }}</option></select
              ></label
            >
            <label>{{ t('edu.reason') }}<textarea v-model="reason" required maxlength="1000" /></label>
            <button type="submit">{{
              participant.revision ? t('edu.correctAttendance') : t('edu.recordAttendance')
            }}</button
            ><p>{{ t('edu.correctionHelp') }}</p>
          </fieldset></form
        >
        <h3>{{ t('edu.feedback') }}</h3>
        <form @submit.prevent="draft"
          ><fieldset :disabled="busy || !participant.revision">
            <label
              >{{ t('edu.feedbackContent') }}<textarea v-model="feedbackContent" required maxlength="4000" />
            </label>
            <button type="submit">{{ t('edu.saveDraft') }}</button>
          </fieldset></form
        >
        <article v-for="(item, index) in feedback" :key="item.id" class="edu-card">
          <strong>{{ t('edu.version') }} {{ item.revision }} · {{ t(`edu.state.${item.status}`) }}</strong
          ><p class="edu-feedback-content">{{ item.content }}</p>
          <button
            v-if="index === 0 && item.status === 'DRAFT'"
            type="button"
            :disabled="busy"
            @click="confirm(item.id)"
            >{{ t('edu.confirmFeedback') }}</button
          >
          <button v-if="index === 0 && item.status !== 'DRAFT'" type="button" @click="copy(item.content)">{{
            t('edu.copy')
          }}</button>
          <button
            v-if="canManage && index === 0 && item.status === 'CONFIRMED'"
            type="button"
            :disabled="busy"
            @click="prepareSend(item.id)"
            >{{ t('edu.reportSent') }}</button
          >
          <p v-if="item.status === 'MANUAL_REPORTED'">{{ t('edu.deliveryUnverified') }}</p>
        </article>
        <p v-if="copied" role="status">{{ t('edu.copiedHelp') }}</p>
        <form v-if="sendId" @submit.prevent="reportSent"
          ><fieldset :disabled="busy"
            ><p>{{ t('edu.manualSendHelp') }}</p>
            <label
              >{{ t('edu.recipient')
              }}<select v-model="recipientId" required
                ><option value="">{{ t('edu.choose') }}</option
                ><option v-for="recipient in recipients" :key="recipient.id" :value="recipient.id">{{
                  recipient.name
                }}</option></select
              ></label
            >
            <label>{{ t('edu.sentTime') }}<input v-model="sentTime" type="datetime-local" step="1" required /></label>
            <label>{{ t('edu.sendEvidence') }}<textarea v-model="sendEvidence" required maxlength="1000" /></label>
            <label
              >{{ t('edu.consentReference') }}<textarea v-model="consentReference" required maxlength="1000" />
            </label>
            <button type="submit">{{ t('edu.reportSent') }}</button>
          </fieldset></form
        >
      </section>
    </template>
  </main>
</template>

<script setup lang="ts">
  import { computed, ref, watch } from 'vue';

  import type { EducationApi } from '@lib/shared/api/modules/education';
  import { useI18n } from '@lib/shared/hooks/useI18n';
  import type {
    EducationClass,
    EducationFeedback,
    EducationParticipant,
    EducationQueueItem,
    EducationSession,
    EducationTeacher,
  } from '@lib/shared/models/education';

  import './education.css';

  const props = defineProps<{ api: EducationApi; canManage: boolean }>();
  const { t } = useI18n();
  const classes = ref<EducationClass[]>([]);
  const teachers = ref<EducationTeacher[]>([]);
  const sessions = ref<EducationSession[]>([]);
  const queue = ref<EducationQueueItem[]>([]);
  const participants = ref<EducationParticipant[]>([]);
  const feedback = ref<EducationFeedback[]>([]);
  const recipients = ref<EducationTeacher[]>([]);
  const busy = ref(false);
  const loading = ref(false);
  const failed = ref(false);
  const saved = ref(false);
  const copied = ref(false);
  const className = ref('');
  const classMode = ref('HOURS');
  const classId = ref('');
  const teacherId = ref('');
  const roomKey = ref('');
  const startsAt = ref('');
  const endsAt = ref('');
  const makeupFrom = ref('');
  const selectedSession = ref('');
  const selectedEnrollment = ref('');
  const attendanceStatus = ref('PRESENT');
  const reason = ref('');
  const feedbackContent = ref('');
  const sendId = ref('');
  const recipientId = ref('');
  const sentTime = ref('');
  const sendEvidence = ref('');
  const consentReference = ref('');
  let scheduleKey = crypto.randomUUID();
  let attendanceKey = crypto.randomUUID();
  let feedbackKey = crypto.randomUUID();
  let listVersion = 0;
  let participantVersion = 0;
  let feedbackVersion = 0;
  const session = computed(() => sessions.value.find((s) => s.id === selectedSession.value));
  const participant = computed(() => participants.value.find((p) => p.enrollmentId === selectedEnrollment.value));
  const date = (value: number) => new Date(value).toLocaleString();
  async function loadFeedback() {
    const version = ++feedbackVersion;
    feedback.value = [];
    sendId.value = '';
    copied.value = false;
    if (!selectedSession.value || !selectedEnrollment.value) return;
    try {
      const result = await props.api.feedback(selectedSession.value, selectedEnrollment.value);
      if (version !== feedbackVersion) return;
      feedback.value = result;
      feedbackContent.value = result[0]?.content ?? t('edu.feedbackTemplate');
      attendanceStatus.value = participant.value?.attendanceStatus ?? 'PRESENT';
    } catch {
      if (version === feedbackVersion) failed.value = true;
    }
  }
  async function loadParticipants() {
    const version = ++participantVersion;
    const id = selectedSession.value;
    if (!id) {
      participants.value = [];
      return;
    }
    try {
      const result = await props.api.participants(id);
      if (version === participantVersion) {
        participants.value = result;
        await loadFeedback();
      }
    } catch {
      if (version === participantVersion) failed.value = true;
    }
  }
  async function load() {
    const version = ++listVersion;
    loading.value = true;
    failed.value = false;
    try {
      const [sessionData, queueData, classData, teacherData] = await Promise.all([
        props.api.sessions(),
        props.api.queue(),
        props.canManage ? props.api.classes() : Promise.resolve([]),
        props.canManage ? props.api.teachers() : Promise.resolve([]),
      ]);
      if (version !== listVersion) return;
      sessions.value = sessionData;
      queue.value = queueData;
      classes.value = classData;
      teachers.value = teacherData;
      await loadParticipants();
    } catch {
      if (version === listVersion) failed.value = true;
    } finally {
      if (version === listVersion) loading.value = false;
    }
  }
  async function run(action: () => Promise<unknown>) {
    if (busy.value) return false;
    busy.value = true;
    failed.value = false;
    saved.value = false;
    try {
      await action();
      saved.value = true;
      await load();
      return true;
    } catch {
      failed.value = true;
      return false;
    } finally {
      busy.value = false;
    }
  }
  function selectSession(id: string) {
    selectedSession.value = id;
  }
  async function createClass() {
    if (await run(() => props.api.createClass({ name: className.value, mode: classMode.value }))) className.value = '';
  }
  async function schedule() {
    if (
      await run(() =>
        props.api.schedule({
          classId: classId.value,
          requestKey: scheduleKey,
          teacherId: teacherId.value,
          roomKey: roomKey.value,
          startsAt: new Date(startsAt.value).getTime(),
          endsAt: new Date(endsAt.value).getTime(),
          makeupForSessionId: makeupFrom.value || undefined,
        })
      )
    )
      scheduleKey = crypto.randomUUID();
  }
  async function cancel() {
    // eslint-disable-next-line no-alert
    if (window.confirm(t('edu.cancelConfirm'))) await run(() => props.api.cancelSession(selectedSession.value));
  }
  async function record() {
    const learner = participant.value;
    if (!learner) return;
    if (
      await run(() =>
        props.api.recordAttendance(selectedSession.value, learner.enrollmentId, {
          requestKey: attendanceKey,
          expectedRevision: learner.revision,
          status: attendanceStatus.value,
          reason: reason.value,
        })
      )
    ) {
      attendanceKey = crypto.randomUUID();
      reason.value = '';
    }
  }
  async function draft() {
    if (
      await run(() =>
        props.api.draftFeedback({
          sessionId: selectedSession.value,
          enrollmentId: selectedEnrollment.value,
          requestKey: feedbackKey,
          previousId: feedback.value[0]?.id,
          content: feedbackContent.value,
        })
      )
    )
      feedbackKey = crypto.randomUUID();
  }
  async function confirm(id: string) {
    await run(() => props.api.confirmFeedback(id));
  }
  async function copy(content: string) {
    try {
      await navigator.clipboard.writeText(content);
      copied.value = true;
    } catch {
      failed.value = true;
    }
  }
  async function prepareSend(id: string) {
    const enrollment = selectedEnrollment.value;
    try {
      const result = await props.api.recipients(enrollment);
      if (enrollment === selectedEnrollment.value) {
        recipients.value = result;
        sendId.value = id;
      }
    } catch {
      failed.value = true;
    }
  }
  async function reportSent() {
    await run(() =>
      props.api.reportFeedbackSent(sendId.value, {
        recipientContactId: recipientId.value,
        sentTime: new Date(sentTime.value).getTime(),
        sendEvidence: sendEvidence.value,
        consentReference: consentReference.value,
      })
    );
  }
  watch(selectedSession, () => {
    participantVersion++;
    feedbackVersion++;
    participants.value = [];
    selectedEnrollment.value = '';
    loadParticipants();
  });
  watch(selectedEnrollment, () => {
    feedbackVersion++;
    attendanceKey = crypto.randomUUID();
    feedbackKey = crypto.randomUUID();
    reason.value = '';
    loadFeedback();
  });
  load();
</script>

<style scoped src="./education.css"></style>

<style scoped>
  .edu-feedback-content {
    white-space: pre-wrap;
  }
</style>
