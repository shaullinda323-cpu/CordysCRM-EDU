<template>
  <section class="edu-panel">
    <p>{{ t('education.intro') }}</p
    ><p v-if="failed" role="alert">{{ t('edu.failed') }}</p>
    <button type="button" :disabled="busy" @click="load">{{ t('edu.refresh') }}</button>
    <form v-if="canEdit" @submit.prevent="add"
      ><fieldset :disabled="busy">
        <label>{{ t('education.name') }}<input v-model="name" required maxlength="100" /></label>
        <label>{{ t('education.grade') }}<input v-model="grade" maxlength="50" /></label>
        <button type="submit">{{ t('education.add') }}</button>
      </fieldset></form
    >
    <label
      >{{ t('education.select')
      }}<select v-model="selected"
        ><option value="">{{ t('edu.choose') }}</option
        ><option v-for="student in students" :key="student.id" :value="student.id"
          >{{ student.name }} · {{ student.grade }}</option
        ></select
      ></label
    >
    <template v-if="selected">
      <h3>{{ t('education.guardians') }}</h3
      ><ul
        ><li v-for="guardian in guardians" :key="guardian.id"
          >{{ contactName(guardian.contactId) }} · {{ guardian.relationship }}</li
        ></ul
      >
      <form v-if="canEdit && canReadContacts" @submit.prevent="link"
        ><fieldset :disabled="busy"
          ><p>{{ t('education.contactHelp') }}</p>
          <label
            >{{ t('education.contact')
            }}<select v-model="contactId" required
              ><option value="">{{ t('edu.choose') }}</option
              ><option
                v-for="contact in contacts.filter((c) => c.enable === true)"
                :key="contact.id"
                :value="contact.id"
                >{{ contact.name }}</option
              ></select
            ></label
          >
          <label>{{ t('education.relationship') }}<input v-model="relationship" required maxlength="30" /></label>
          <button type="submit">{{ t('education.link') }}</button>
        </fieldset></form
      >
      <StudentFlow
        :key="selected"
        :api="educationApi"
        :customer-id="customerId"
        :student-id="selected"
        :can-edit="canEdit"
        :can-read-orders="hasAnyPermission(['ORDER:READ'])"
        :can-receive="hasAnyPermission(['CONTRACT_PAYMENT_RECORD:READ'])"
      />
    </template>
  </section>
</template>

<script setup lang="ts">
  import { computed, ref, watch } from 'vue';

  import StudentFlow from '@lib/shared/components/education/student-flow.vue';
  import { useI18n } from '@lib/shared/hooks/useI18n';
  import type { EducationGuardian, EducationStudent } from '@lib/shared/models/education';

  import { educationApi, getContactListUnderCustomer } from '@/api/modules';
  import { hasAnyPermission } from '@/utils/permission';

  const props = defineProps<{ customerId: string; readonly?: boolean }>();
  const { t } = useI18n();
  const canEdit = computed(() => !props.readonly && hasAnyPermission(['CUSTOMER_MANAGEMENT:UPDATE']));
  const canReadContacts = computed(() => hasAnyPermission(['CUSTOMER_MANAGEMENT_CONTACT:READ']));
  const students = ref<EducationStudent[]>([]);
  const guardians = ref<EducationGuardian[]>([]);
  const contacts = ref<{ id: string; name: string; enable?: boolean }[]>([]);
  const selected = ref('');
  const name = ref('');
  const grade = ref('');
  const contactId = ref('');
  const relationship = ref('');
  const busy = ref(false);
  const failed = ref(false);
  let generation = 0;
  let guardianGeneration = 0;
  const contactName = (id: string) => contacts.value.find((c) => c.id === id)?.name ?? t('education.linkedContact');
  async function load() {
    const version = ++generation;
    failed.value = false;
    try {
      const [children, contactData] = await Promise.all([
        educationApi.getEducationStudents(props.customerId),
        canReadContacts.value ? getContactListUnderCustomer({ id: props.customerId }) : Promise.resolve({ list: [] }),
      ]);
      if (version !== generation) return;
      students.value = children;
      contacts.value = contactData.list ?? [];
      if (!children.some((c) => c.id === selected.value)) selected.value = children[0]?.id ?? '';
    } catch {
      if (version === generation) failed.value = true;
    }
  }
  async function loadGuardians() {
    const version = ++guardianGeneration;
    guardians.value = [];
    if (!selected.value) return;
    try {
      const result = await educationApi.getEducationGuardians(selected.value);
      if (version === guardianGeneration) guardians.value = result;
    } catch {
      if (version === guardianGeneration) failed.value = true;
    }
  }
  async function add() {
    if (busy.value || !canEdit.value) return;
    busy.value = true;
    const customer = props.customerId;
    try {
      const child = await educationApi.createEducationStudent({
        customerId: customer,
        name: name.value.trim(),
        grade: grade.value.trim(),
      });
      if (customer !== props.customerId) return;
      await load();
      if (customer !== props.customerId) return;
      selected.value = child.id;
      name.value = '';
      grade.value = '';
    } catch {
      failed.value = true;
    } finally {
      busy.value = false;
    }
  }
  async function link() {
    if (busy.value || !canEdit.value || !selected.value) return;
    busy.value = true;
    const student = selected.value;
    try {
      await educationApi.linkEducationGuardian({
        studentId: student,
        contactId: contactId.value,
        relationship: relationship.value.trim(),
      });
      if (student === selected.value) {
        await loadGuardians();
        contactId.value = '';
        relationship.value = '';
      }
    } catch {
      failed.value = true;
    } finally {
      busy.value = false;
    }
  }
  watch(selected, () => {
    contactId.value = '';
    relationship.value = '';
    loadGuardians();
  });
  watch(
    () => props.customerId,
    () => {
      generation++;
      guardianGeneration++;
      students.value = [];
      contacts.value = [];
      selected.value = '';
      load();
    },
    { immediate: true }
  );
</script>

<style scoped src="../../../../lib-shared/components/education/education.css"></style>
