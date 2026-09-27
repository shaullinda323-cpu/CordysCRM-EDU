<template>
  <div class="h-full overflow-auto p-[24px]">
    <n-space vertical :size="16">
      <p class="text-[var(--text-n3)]">{{ t('education.intro') }}</p>
      <n-alert v-if="failed" type="error" :title="t('education.error')">
        <n-button @click="loadFamily">{{ t('education.retry') }}</n-button>
      </n-alert>
      <n-spin :show="loading">
        <n-space v-if="canEdit" align="center">
          <n-input v-model:value="name" :maxlength="100" :placeholder="t('education.name')" :disabled="saving" />
          <n-input v-model:value="grade" :maxlength="50" :placeholder="t('education.grade')" :disabled="saving" />
          <n-button type="primary" :loading="saving" :disabled="!name.trim() || loading" @click="addStudent">
            {{ t('education.add') }}
          </n-button>
        </n-space>
        <n-empty v-if="!loading && !failed && !students.length" class="py-[32px]" :description="t('education.empty')" />
        <n-list v-else>
          <n-list-item v-for="student in students" :key="student.id">
            <n-button text :type="selected === student.id ? 'primary' : 'default'" @click="selected = student.id">
              {{ student.name }}{{ student.grade ? ` · ${student.grade}` : '' }}
            </n-button>
          </n-list-item>
        </n-list>
      </n-spin>
      <n-card v-if="selected" :title="t('education.guardians')">
        <n-spin :show="guardianLoading">
          <n-alert v-if="guardianFailed" type="error" :title="t('education.error')">
            <n-button @click="loadGuardians">{{ t('education.retry') }}</n-button>
          </n-alert>
          <n-list v-else-if="guardians.length">
            <n-list-item v-for="guardian in guardians" :key="guardian.id">
              {{ contactName(guardian.contactId) }} · {{ guardian.relationship }}
            </n-list-item>
          </n-list>
          <n-empty v-else-if="!guardianLoading" :description="t('education.noGuardians')" />
          <n-space v-if="canEdit" vertical class="mt-[16px]">
            <p class="text-[var(--text-n3)]">{{ t('education.contactHelp') }}</p>
            <n-select v-model:value="contactId" :options="contactOptions" :placeholder="t('education.contact')" />
            <n-input v-model:value="relationship" :maxlength="30" :placeholder="t('education.relationship')" />
            <n-button
              :loading="linking"
              :disabled="!contactId || !relationship.trim() || guardianLoading"
              @click="linkGuardian"
            >
              {{ t('education.link') }}
            </n-button>
          </n-space>
        </n-spin>
      </n-card>
      <StudentFlow
        v-if="selected"
        :key="selected"
        :api="educationApi"
        :customer-id="customerId"
        :student-id="selected"
        :can-edit="canEdit"
        :can-read-orders="hasAnyPermission(['ORDER:READ'])"
        :can-receive="hasAnyPermission(['CONTRACT_PAYMENT_RECORD:READ'])"
      />
    </n-space>
  </div>
</template>

<script setup lang="ts">
  import { computed, ref, watch } from 'vue';
  import {
    NAlert,
    NButton,
    NCard,
    NEmpty,
    NInput,
    NList,
    NListItem,
    NSelect,
    NSpace,
    NSpin,
    useMessage,
  } from 'naive-ui';

  import StudentFlow from '@lib/shared/components/education/student-flow.vue';
  import { useI18n } from '@lib/shared/hooks/useI18n';
  import type { EducationGuardian, EducationStudent } from '@lib/shared/models/education';

  import {
    createEducationStudent,
    educationApi,
    getContactListUnderCustomer,
    getEducationGuardians,
    getEducationStudents,
    linkEducationGuardian,
  } from '@/api/modules';
  import { hasAnyPermission } from '@/utils/permission';

  const props = defineProps<{ customerId: string; readonly?: boolean }>();
  const { t } = useI18n();
  const message = useMessage();
  const canEdit = computed(() => !props.readonly && hasAnyPermission(['CUSTOMER_MANAGEMENT:UPDATE']));
  const students = ref<EducationStudent[]>([]);
  const guardians = ref<EducationGuardian[]>([]);
  const contacts = ref<{ id: string; name: string; enable?: boolean }[]>([]);
  const selected = ref('');
  const name = ref('');
  const grade = ref('');
  const contactId = ref<string | null>(null);
  const relationship = ref('');
  const loading = ref(false);
  const guardianLoading = ref(false);
  const saving = ref(false);
  const linking = ref(false);
  const failed = ref(false);
  const guardianFailed = ref(false);
  let familyRequest = 0;
  let guardianRequest = 0;
  const contactOptions = computed(() =>
    contacts.value
      .filter((contact) => contact.enable === true)
      .map((contact) => ({ label: contact.name, value: contact.id }))
  );

  function contactName(id: string) {
    return contacts.value.find((contact) => contact.id === id)?.name ?? t('education.linkedContact');
  }

  async function loadFamily() {
    const request = ++familyRequest;
    loading.value = true;
    failed.value = false;
    try {
      const [children, contactData] = await Promise.all([
        getEducationStudents(props.customerId),
        hasAnyPermission(['CUSTOMER_MANAGEMENT_CONTACT:READ'])
          ? getContactListUnderCustomer({ id: props.customerId })
          : Promise.resolve({ list: [] }),
      ]);
      // A slow response for a previously opened customer must not overwrite this family.
      if (request !== familyRequest) return;
      students.value = children;
      contacts.value = contactData.list ?? [];
      if (!children.some((child) => child.id === selected.value)) selected.value = children[0]?.id ?? '';
    } catch {
      if (request === familyRequest) failed.value = true;
    } finally {
      if (request === familyRequest) loading.value = false;
    }
  }

  async function loadGuardians() {
    const request = ++guardianRequest;
    guardians.value = [];
    guardianFailed.value = false;
    guardianLoading.value = !!selected.value;
    if (!selected.value) return;
    try {
      const result = await getEducationGuardians(selected.value);
      if (request === guardianRequest) guardians.value = result;
    } catch {
      if (request === guardianRequest) guardianFailed.value = true;
    } finally {
      if (request === guardianRequest) guardianLoading.value = false;
    }
  }

  async function addStudent() {
    if (saving.value || !canEdit.value || !name.value.trim()) return;
    saving.value = true;
    const { customerId } = props;
    try {
      const child = await createEducationStudent({ customerId, name: name.value.trim(), grade: grade.value.trim() });
      if (customerId !== props.customerId) return;
      name.value = '';
      grade.value = '';
      await loadFamily();
      if (customerId !== props.customerId) return;
      selected.value = child.id;
      message.success(t('education.saved'));
    } catch {
      // The shared HTTP client presents server validation errors.
    } finally {
      saving.value = false;
    }
  }

  async function linkGuardian() {
    if (linking.value || !canEdit.value || !contactId.value || !selected.value || !relationship.value.trim()) return;
    linking.value = true;
    const studentId = selected.value;
    try {
      await linkEducationGuardian({ studentId, contactId: contactId.value, relationship: relationship.value.trim() });
      if (selected.value === studentId) {
        contactId.value = null;
        relationship.value = '';
        await loadGuardians();
        message.success(t('education.saved'));
      }
    } catch {
      // The shared HTTP client presents server validation errors.
    } finally {
      linking.value = false;
    }
  }

  watch(selected, () => {
    contactId.value = null;
    relationship.value = '';
    loadGuardians();
  });
  watch(
    () => props.customerId,
    () => {
      students.value = [];
      contacts.value = [];
      selected.value = '';
      name.value = '';
      grade.value = '';
      loadFamily();
    },
    { immediate: true }
  );
</script>
