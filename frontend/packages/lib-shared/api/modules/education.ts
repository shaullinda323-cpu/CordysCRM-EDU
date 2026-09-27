import type { CordysAxios } from '@lib/shared/api/http/Axios';
import type {
  EducationAttendance,
  EducationClass,
  EducationCredit,
  EducationEnrollment,
  EducationEnrollmentCreate,
  EducationFeedback,
  EducationGuardian,
  EducationGuardianLink,
  EducationOrderRow,
  EducationParticipant,
  EducationQueueItem,
  EducationReceipt,
  EducationSession,
  EducationSessionCreate,
  EducationStudent,
  EducationStudentCreate,
  EducationTeacher,
  EducationTrial,
} from '@lib/shared/models/education';

export default function useEducationApi(CDR: CordysAxios) {
  const path = (id: string) => encodeURIComponent(id);
  return {
    trials: (studentId: string) => CDR.get<EducationTrial[]>({ url: '/education/trials', params: { studentId } }),
    bookTrial: (data: { studentId: string; requestKey: string; topic: string; startsAt: number; endsAt: number }) =>
      CDR.post<EducationTrial>({ url: '/education/trials', data }),
    trialResult: (id: string, data: { status: string; outcome: string }) =>
      CDR.post<EducationTrial>({ url: `/education/trials/${path(id)}/result`, data }),
    enrollments: (studentId: string) =>
      CDR.get<EducationEnrollment[]>({ url: '/education/enrollments', params: { studentId } }),
    enroll: (data: EducationEnrollmentCreate) => CDR.post<EducationEnrollment>({ url: '/education/enrollments', data }),
    orderRows: (customerId: string) =>
      CDR.get<EducationOrderRow[]>({ url: '/education/options/order-rows', params: { customerId } }),
    receipts: (enrollmentId: string) =>
      CDR.get<EducationReceipt[]>({ url: '/education/options/receipts', params: { enrollmentId } }),
    allocate: (id: string, data: { receiptId: string; amount: number }) =>
      CDR.post<EducationEnrollment>({ url: `/education/enrollments/${path(id)}/payments`, data }),
    handoff: (id: string) => CDR.post<EducationEnrollment>({ url: `/education/enrollments/${path(id)}/handoff` }),
    classes: () => CDR.get<EducationClass[]>({ url: '/education/teaching/classes' }),
    createClass: (data: { name: string; mode: string }) =>
      CDR.post<EducationClass>({ url: '/education/teaching/classes', data }),
    join: (id: string, data: { enrollmentId: string; effectiveFrom: number; effectiveTo?: number }) =>
      CDR.post<void>({ url: `/education/teaching/classes/${path(id)}/members`, data }),
    recipients: (enrollmentId: string) =>
      CDR.get<EducationTeacher[]>({ url: '/education/options/recipients', params: { enrollmentId } }),
    teachers: () => CDR.get<EducationTeacher[]>({ url: '/education/options/teachers' }),
    sessions: () => CDR.get<EducationSession[]>({ url: '/education/teaching/sessions' }),
    schedule: (data: EducationSessionCreate) =>
      CDR.post<EducationSession>({ url: '/education/teaching/sessions', data }),
    cancelSession: (id: string) => CDR.post<void>({ url: `/education/teaching/sessions/${path(id)}/cancel` }),
    participants: (id: string) =>
      CDR.get<EducationParticipant[]>({ url: `/education/teaching/sessions/${path(id)}/participants` }),
    recordAttendance: (
      session: string,
      enrollment: string,
      data: { requestKey: string; expectedRevision: number; status: string; reason: string }
    ) =>
      CDR.post<EducationAttendance>({
        url: `/education/teaching/sessions/${path(session)}/attendance/${path(enrollment)}`,
        data,
      }),
    ledger: (id: string) => CDR.get<EducationCredit[]>({ url: `/education/teaching/enrollments/${path(id)}/ledger` }),
    feedback: (sessionId: string, enrollmentId: string) =>
      CDR.get<EducationFeedback[]>({ url: '/education/feedback', params: { sessionId, enrollmentId } }),
    draftFeedback: (data: {
      sessionId: string;
      enrollmentId: string;
      requestKey: string;
      previousId?: string;
      content: string;
    }) => CDR.post<EducationFeedback>({ url: '/education/feedback', data }),
    confirmFeedback: (id: string) => CDR.post<EducationFeedback>({ url: `/education/feedback/${path(id)}/confirm` }),
    reportFeedbackSent: (
      id: string,
      data: { recipientContactId: string; sentTime: number; sendEvidence: string; consentReference: string }
    ) => CDR.post<EducationFeedback>({ url: `/education/feedback/${path(id)}/report-sent`, data }),
    queue: () => CDR.get<EducationQueueItem[]>({ url: '/education/teaching/queue' }),
    getEducationStudents: (customerId: string) =>
      CDR.get<EducationStudent[]>({ url: '/education/students', params: { customerId } }),
    createEducationStudent: (data: EducationStudentCreate) =>
      CDR.post<EducationStudent>({ url: '/education/students', data }),
    getEducationGuardians: (studentId: string) =>
      CDR.get<EducationGuardian[]>({ url: `/education/students/${encodeURIComponent(studentId)}/guardians` }),
    linkEducationGuardian: (data: EducationGuardianLink) =>
      CDR.post<EducationGuardian>({ url: '/education/students/guardians', data }),
  };
}

export type EducationApi = ReturnType<typeof useEducationApi>;
