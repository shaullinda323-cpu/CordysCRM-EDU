<template><TeachingWorkbench :api="educationApi" :can-manage="canManage" /></template>

<script setup lang="ts">
  import { computed } from 'vue';

  import TeachingWorkbench from '@lib/shared/components/education/teaching-workbench.vue';

  import { educationApi } from '@/api/modules';
  import { hasAnyPermission } from '@/utils/permission';

  const canManage = computed(() => hasAnyPermission(['CUSTOMER_MANAGEMENT:UPDATE']));
</script>
