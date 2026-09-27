import copy
import unittest
from reconcile import TABLES, reconcile


def fixture():
    data = {table: [] for table in TABLES}
    data['customers'] = [dict(id='family', organization_id='org')]
    data['students'] = [dict(id='child', organization_id='org', customer_id='family')]
    data['contacts'] = [dict(id='parent', organization_id='org', customer_id='family')]
    data['guardians'] = [dict(id='link', organization_id='org', customer_id='family', student_id='child', contact_id='parent')]
    data['enrollments'] = [dict(id='course', organization_id='org', customer_id='family', student_id='child', mode='HOURS', total_units='6', amount='100')]
    data['payments'] = [dict(id='payment', organization_id='org', enrollment_id='course', receipt_id='receipt', receipt_total='100', amount='100')]
    data['ledger'] = [dict(id='grant', organization_id='org', enrollment_id='course', kind='GRANT', units='6', operation_key='grant')]
    return data


class ReconciliationTest(unittest.TestCase):
    def test_equal_and_post_cutover_events_preserved(self):
        before = fixture()
        after = copy.deepcopy(before)
        after['ledger'].append(dict(id='attendance', organization_id='org', enrollment_id='course', kind='DEBIT', units='-1', operation_key='attend'))
        result = reconcile(before, after)
        self.assertTrue(result['passed'])
        self.assertEqual(result['additional_records']['ledger'], 1)
        self.assertEqual(len(after['ledger']), 2)

    def test_cross_org_and_missing_mapping(self):
        before = fixture()
        after = copy.deepcopy(before)
        after['students'][0]['organization_id'] = 'foreign'
        self.assertFalse(reconcile(before, after)['passed'])

    def test_negative_balance_and_receipt_overspend(self):
        before = fixture()
        after = copy.deepcopy(before)
        after['ledger'].append(dict(id='bad', organization_id='org', enrollment_id='course', kind='DEBIT', units='-7', operation_key='bad'))
        after['payments'][0]['amount'] = '101'
        errors = reconcile(before, after)['errors']
        self.assertTrue(any('negative entitlement' in value for value in errors))
        self.assertTrue(any('overallocated receipt' in value for value in errors))

    def test_duplicate_ids_and_operation(self):
        before = fixture()
        after = copy.deepcopy(before)
        after['students'].append(after['students'][0].copy())
        self.assertFalse(reconcile(before, after)['passed'])
        after = copy.deepcopy(before)
        after['ledger'].append(dict(after['ledger'][0], id='duplicate'))
        self.assertFalse(reconcile(before, after)['passed'])

    def test_invalid_number_does_not_pass_or_crash(self):
        before = fixture()
        after = copy.deepcopy(before)
        after['enrollments'][0]['amount'] = 'NaN'
        self.assertFalse(reconcile(before, after)['passed'])


if __name__ == '__main__':
    unittest.main()
