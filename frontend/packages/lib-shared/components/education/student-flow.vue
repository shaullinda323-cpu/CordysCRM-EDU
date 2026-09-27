<template>
  <section class="edu-panel" :aria-busy="busy || loading">
    <p v-if="loading" role="status">{{ t('edu.loading') }}</p>
    <p v-if="failed" role="alert">{{ t('edu.failed') }}</p>
    <p v-if="saved" role="status">{{ t('education.saved') }}</p>
    <button type="button" :disabled="busy" @click="load">{{ t('education.retry') }}</button>
    <h3>{{ t('edu.trials') }}</h3>
    <form v-if="canEdit" @submit.prevent="book">
      <fieldset :disabled="busy"
        ><div class="edu-grid">
          <label>{{ t('edu.topic') }}<input v-model="topic" required maxlength="100" /></label>
          <label>{{ t('edu.start') }}<input v-model="trialStart" type="datetime-local" required /></label>
          <label>{{ t('edu.end') }}<input v-model="trialEnd" type="datetime-local" required /></label> </div
        ><button type="submit">{{ t('edu.book') }}</button></fieldset
      >
    </form>
    <p v-if="!trials.length && !loading">{{ t('edu.empty') }}</p>
    <article v-for="trial in trials" :key="trial.id" class="edu-card">
      <strong>{{ trial.topic }}</strong
      ><p>{{ date(trial.startsAt) }} — {{ date(trial.endsAt) }}</p>
      <p>{{ t(`edu.state.${trial.status}`) }} · {{ trial.outcome }}</p>
      <button v-if="canEdit && trial.status === 'BOOKED'" type="button" @click="trialId = trial.id">{{
        t('edu.recordTrial')
      }}</button>
    </article>
    <form v-if="canEdit && trialId" @submit.prevent="finishTrial"
      ><fieldset :disabled="busy">
        <label
          >{{ t('edu.outcome')
          }}<select v-model="trialStatus"
            ><option v-for="status in ['ATTENDED', 'NO_SHOW', 'CANCELLED']" :key="status" :value="status">{{
              t(`edu.state.${status}`)
            }}</option></select
          ></label
        >
        <label>{{ t('edu.notes') }}<textarea v-model="outcome" required maxlength="2000" /></label>
        <button type="submit">{{ t('edu.save') }}</button>
      </fieldset></form
    >
    <template v-if="canReadOrders">
      <h3>{{ t('edu.enrollments') }}</h3
      ><p>{{ t('edu.saleHelp') }}</p>
      <form v-if="canEdit" @submit.prevent="enroll"
        ><fieldset :disabled="busy"
          ><div class="edu-grid">
            <label
              >{{ t('edu.orderRow')
              }}<select v-model="rowIndex" required
                ><option value="">{{ t('edu.choose') }}</option
                ><option v-for="(row, index) in rows" :key="`${row.orderId}:${row.rowId}`" :value="String(index)"
                  >{{ row.orderName }} · {{ t('edu.row') }} {{ row.rowNumber }} · {{ row.amount }}</option
                ></select
              ></label
            >
            <label>{{ t('edu.course') }}<input v-model="title" required maxlength="100" /></label>
            <label
              >{{ t('edu.mode')
              }}<select v-model="mode"
                ><option value="HOURS">{{ t('edu.hours') }}</option
                ><option value="EVENT">{{ t('edu.event') }}</option></select
              ></label
            >
            <label
              >{{ t('edu.units')
              }}<input
                v-model.number="units"
                type="number"
                :disabled="mode === 'EVENT'"
                min="0.01"
                max="10000"
                step="0.01"
                required
            /></label>
            <label
              >{{ t('edu.renewFrom')
              }}<select v-model="renewalFrom"
                ><option value="">{{ t('edu.newEnrollment') }}</option
                ><option
                  v-for="item in enrollments.filter((e) => e.status === 'ACTIVE')"
                  :key="item.id"
                  :value="item.id"
                  >{{ item.title }}</option
                ></select
              ></label
            > </div
          ><button type="submit" :disabled="!rows.length">{{ t('edu.enroll') }}</button></fieldset
        ></form
      >
      <p v-if="!enrollments.length && !loading">{{ t('edu.empty') }}</p>
      <article v-for="item in enrollments" :key="item.id" class="edu-card">
        <strong>{{ item.title }}</strong
        ><p
          >{{ t(`edu.state.${item.status}`) }} · {{ item.totalUnits }}
          {{ item.mode === 'HOURS' ? t('edu.hours') : t('edu.event') }}</p
        >
        <p>{{ t('edu.receivable') }} {{ item.amount }} · {{ t('edu.allocated') }} {{ item.allocatedAmount }}</p>
        <button type="button" :disabled="busy" @click="selectedEnrollment = item.id">{{ t('edu.manage') }}</button>
      </article>
      <section v-if="current" class="edu-card"
        ><h4>{{ current.title }}</h4>
        <template v-if="canEdit && current.status === 'PENDING'">
          <form v-if="canReceive" @submit.prevent="allocate"
            ><fieldset :disabled="busy">
              <label
                >{{ t('edu.receipt')
                }}<select v-model="receiptId" required
                  ><option value="">{{ t('edu.choose') }}</option
                  ><option v-for="receipt in receipts" :key="receipt.id" :value="receipt.id"
                    >{{ receipt.name }} · {{ t('edu.available') }} {{ receipt.availableAmount }}</option
                  ></select
                ></label
              >
              <label
                >{{ t('edu.allocateAmount')
                }}<input v-model.number="paymentAmount" type="number" min="0.01" step="0.01" required
              /></label>
              <button type="submit">{{ t('edu.allocate') }}</button>
            </fieldset></form
          >
          <p>{{ t('edu.handoffHelp') }}</p
          ><button type="button" :disabled="busy || current.allocatedAmount < current.amount" @click="handoff">{{
            t('edu.handoff')
          }}</button>
        </template>
        <form v-if="canEdit && current.status === 'ACTIVE'" @submit.prevent="join"
          ><fieldset :disabled="busy">
            <label
              >{{ t('edu.class')
              }}<select v-model="classId" required
                ><option value="">{{ t('edu.choose') }}</option
                ><option
                  v-for="group in classes.filter((c) => c.mode === current?.mode)"
                  :key="group.id"
                  :value="group.id"
                  >{{ group.name }}</option
                ></select
              ></label
            >
            <label>{{ t('edu.effectiveFrom') }}<input v-model="effectiveFrom" type="datetime-local" required /></label>
            <button type="submit">{{ t('edu.join') }}</button
            ><p>{{ t('edu.joinHelp') }}</p>
          </fieldset></form
        >
        <h4>{{ t('edu.ledger') }}</h4
        ><ul
          ><li v-for="entry in ledger" :key="entry.id"
            >{{ date(entry.createTime) }} · {{ t(`edu.state.${entry.kind}`) }} · {{ entry.units > 0 ? '+' : ''
            }}{{ entry.units }}</li
          ></ul
        >
      </section>
    </template>
    <a href="/education/index">{{ t('edu.workbench') }}</a>
  </section>
</template>

<script setup lang="ts">
  import { computed, ref, watch } from 'vue';

  import type { EducationApi } from '@lib/shared/api/modules/education';
  import { useI18n } from '@lib/shared/hooks/useI18n';
  import type {
    EducationClass,
    EducationCredit,
    EducationEnrollment,
    EducationOrderRow,
    EducationReceipt,
    EducationTrial,
  } from '@lib/shared/models/education';

  import './education.css';

  const props = defineProps<{
    api: EducationApi;
    customerId: string;
    studentId: string;
    canEdit: boolean;
    canReadOrders: boolean;
    canReceive: boolean;
  }>();
  const { t } = useI18n();
  const trials = ref<EducationTrial[]>([]);
  const enrollments = ref<EducationEnrollment[]>([]);
  const rows = ref<EducationOrderRow[]>([]);
  const receipts = ref<EducationReceipt[]>([]);
  const classes = ref<EducationClass[]>([]);
  const ledger = ref<EducationCredit[]>([]);
  const busy = ref(false);
  const loading = ref(false);
  const failed = ref(false);
  const saved = ref(false);
  const topic = ref('');
  const trialStart = ref('');
  const trialEnd = ref('');
  const trialId = ref('');
  const trialStatus = ref('ATTENDED');
  const outcome = ref('');
  const rowIndex = ref('');
  const title = ref('');
  const mode = ref('HOURS');
  const units = ref(1);
  const renewalFrom = ref('');
  const selectedEnrollment = ref('');
  const receiptId = ref('');
  const paymentAmount = ref(0);
  const classId = ref('');
  const effectiveFrom = ref('');
  let trialKey = crypto.randomUUID();
  let enrollmentKey = crypto.randomUUID();
  let generation = 0;
  let detailGeneration = 0;
  const current = computed(() => enrollments.value.find((e) => e.id === selectedEnrollment.value));
  const date = (value: number) => new Date(value).toLocaleString();
  async function loadDetail() {
    const version = ++detailGeneration;
    receipts.value = [];
    ledger.value = [];
    receiptId.value = '';
    const id = selectedEnrollment.value;
    if (!id) return;
    try {
      const [creditData, receiptData] = await Promise.all([
        props.api.ledger(id),
        props.canEdit && props.canReceive && current.value?.status === 'PENDING'
          ? props.api.receipts(id)
          : Promise.resolve([]),
      ]);
      if (version !== detailGeneration) return;
      ledger.value = creditData;
      receipts.value = receiptData;
      paymentAmount.value = Math.max(0, (current.value?.amount ?? 0) - (current.value?.allocatedAmount ?? 0));
    } catch {
      if (version === detailGeneration) failed.value = true;
    }
  }
  async function load() {
    const version = ++generation;
    loading.value = true;
    failed.value = false;
    try {
      const [trialData, enrollmentData, orderData, classData] = await Promise.all([
        props.api.trials(props.studentId),
        props.canReadOrders ? props.api.enrollments(props.studentId) : Promise.resolve([]),
        props.canReadOrders && props.canEdit ? props.api.orderRows(props.customerId) : Promise.resolve([]),
        props.canEdit ? props.api.classes() : Promise.resolve([]),
      ]);
      if (version !== generation) return;
      trials.value = trialData;
      enrollments.value = enrollmentData;
      rows.value = orderData;
      classes.value = classData;
      if (!enrollmentData.some((e) => e.id === selectedEnrollment.value)) selectedEnrollment.value = '';
      await loadDetail();
    } catch {
      if (version === generation) failed.value = true;
    } finally {
      if (version === generation) loading.value = false;
    }
  }
  async function run(action: () => Promise<unknown>) {
    if (busy.value) return false;
    const student = props.studentId;
    busy.value = true;
    failed.value = false;
    saved.value = false;
    try {
      await action();
      if (student === props.studentId) {
        saved.value = true;
        await load();
      }
      return true;
    } catch {
      failed.value = true;
      return false;
    } finally {
      busy.value = false;
    }
  }
  async function book() {
    if (
      await run(() =>
        props.api.bookTrial({
          studentId: props.studentId,
          requestKey: trialKey,
          topic: topic.value,
          startsAt: new Date(trialStart.value).getTime(),
          endsAt: new Date(trialEnd.value).getTime(),
        })
      )
    ) {
      trialKey = crypto.randomUUID();
      topic.value = '';
    }
  }
  async function finishTrial() {
    if (await run(() => props.api.trialResult(trialId.value, { status: trialStatus.value, outcome: outcome.value })))
      trialId.value = '';
  }
  async function enroll() {
    const row = rows.value[Number(rowIndex.value)];
    if (!row || rowIndex.value === '') return;
    if (
      await run(() =>
        props.api.enroll({
          studentId: props.studentId,
          orderId: row.orderId,
          subFieldId: row.subFieldId,
          orderRowId: row.rowId,
          requestKey: enrollmentKey,
          title: title.value,
          mode: mode.value,
          totalUnits: mode.value === 'EVENT' ? 1 : units.value,
          renewalFromId: renewalFrom.value || undefined,
        })
      )
    ) {
      enrollmentKey = crypto.randomUUID();
      rowIndex.value = '';
      title.value = '';
      renewalFrom.value = '';
    }
  }
  async function allocate() {
    await run(() =>
      props.api.allocate(selectedEnrollment.value, { receiptId: receiptId.value, amount: paymentAmount.value })
    );
  }
  async function handoff() {
    await run(() => props.api.handoff(selectedEnrollment.value));
  }
  async function join() {
    await run(() =>
      props.api.join(classId.value, {
        enrollmentId: selectedEnrollment.value,
        effectiveFrom: new Date(effectiveFrom.value).getTime(),
      })
    );
  }
  watch(selectedEnrollment, loadDetail);
  watch(
    () => props.studentId,
    () => {
      generation++;
      detailGeneration++;
      trials.value = [];
      enrollments.value = [];
      rows.value = [];
      selectedEnrollment.value = '';
      trialId.value = '';
      topic.value = '';
      outcome.value = '';
      trialKey = crypto.randomUUID();
      enrollmentKey = crypto.randomUUID();
      load();
    },
    { immediate: true }
  );
</script>

<style scoped src="./education.css"></style>
