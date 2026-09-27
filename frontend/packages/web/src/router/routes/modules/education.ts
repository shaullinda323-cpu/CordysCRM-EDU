import { DEFAULT_LAYOUT } from '../base';
import type { AppRouteRecordRaw } from '../types';

// Assigned teachers can enter without customer/finance permissions; server projections enforce session scope.
const education: AppRouteRecordRaw = {
  path: '/education',
  name: 'Education',
  redirect: '/education/index',
  component: DEFAULT_LAYOUT,
  meta: { locale: 'edu.workbench', permissions: [], icon: 'iconicon_customer', hideChildrenInMenu: true },
  children: [
    {
      path: 'index',
      name: 'EducationWorkbench',
      component: () => import('@/views/education/index.vue'),
      meta: { locale: 'edu.workbench', permissions: [], isTopMenu: true },
    },
  ],
};
export default education;
