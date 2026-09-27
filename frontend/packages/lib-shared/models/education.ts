export interface EducationStudent {
  id: string;
  customerId: string;
  name: string;
  grade: string | null;
}

export interface EducationGuardian {
  id: string;
  studentId: string;
  contactId: string;
  relationship: string;
}

export interface EducationStudentCreate {
  customerId: string;
  name: string;
  grade?: string;
}

export interface EducationGuardianLink {
  studentId: string;
  contactId: string;
  relationship: string;
}

export interface EducationTrial {
  id: string;
  studentId: string;
  topic: string;
  startsAt: number;
  endsAt: number;
  status: string;
  outcome: string | null;
}
export interface EducationEnrollment {
  id: string;
  studentId: string;
  orderId: string;
  title: string;
  mode: string;
  totalUnits: number;
  amount: number;
  allocatedAmount: number;
  status: string;
  handoffUser: string | null;
  handoffTime: number | null;
  renewalFromId: string | null;
}
export interface EducationOrderRow {
  orderId: string;
  orderName: string;
  subFieldId: string;
  rowId: string;
  rowNumber: number;
  amount: number;
}
export interface EducationReceipt {
  id: string;
  name: string;
  availableAmount: number;
}
export interface EducationClass {
  id: string;
  name: string;
  mode: string;
}
export interface EducationSession {
  id: string;
  classId: string;
  teacherId: string;
  roomKey: string;
  startsAt: number;
  endsAt: number;
  status: string;
  makeupForSessionId: string | null;
  enrollmentIds: string[];
}
export interface EducationParticipant {
  enrollmentId: string;
  studentName: string;
  courseTitle: string;
  mode: string;
  balance: number;
  attendanceStatus: string | null;
  revision: number;
}
export interface EducationAttendance {
  id: string;
  enrollmentId: string;
  sessionId: string;
  status: string;
  revision: number;
  chargedUnits: number;
  balance: number;
}
export interface EducationCredit {
  id: string;
  sessionId: string | null;
  kind: string;
  units: number;
  createTime: number;
  createUser: string;
}
export interface EducationFeedback {
  id: string;
  sessionId: string;
  enrollmentId: string;
  revision: number;
  content: string;
  status: string;
  confirmedUser: string | null;
  confirmedTime: number | null;
  recipientContactId: string | null;
  sentTime: number | null;
  sendEvidence: string | null;
  deliveryStatus: string;
}
export interface EducationQueueItem {
  sessionId: string;
  className: string;
  startsAt: number;
  endsAt: number;
  learners: number;
  pendingAttendance: number;
  pendingFeedback: number;
  pendingSend: number;
}
export interface EducationTeacher {
  id: string;
  name: string;
}
export interface EducationEnrollmentCreate {
  studentId: string;
  orderId: string;
  subFieldId: string;
  orderRowId: string;
  requestKey: string;
  title: string;
  mode: string;
  totalUnits: number;
  renewalFromId?: string;
}
export interface EducationSessionCreate {
  classId: string;
  requestKey: string;
  teacherId: string;
  roomKey: string;
  startsAt: number;
  endsAt: number;
  makeupForSessionId?: string;
}
