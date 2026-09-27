#!/usr/bin/env python3
"""Read-only reconciliation of normalized EDU migration manifests; never connects to a database."""
import argparse
import hashlib
import json
from decimal import Decimal, InvalidOperation
from pathlib import Path

TABLES = ('customers', 'contacts', 'students', 'guardians', 'enrollments', 'payments', 'ledger')
REQUIRED = {
    'customers': ('id', 'organization_id'),
    'contacts': ('id', 'organization_id', 'customer_id'),
    'students': ('id', 'organization_id', 'customer_id'),
    'guardians': ('id', 'organization_id', 'customer_id', 'student_id', 'contact_id'),
    'enrollments': ('id', 'organization_id', 'customer_id', 'student_id', 'mode', 'total_units', 'amount'),
    'payments': ('id', 'organization_id', 'enrollment_id', 'receipt_id', 'receipt_total', 'amount'),
    'ledger': ('id', 'organization_id', 'enrollment_id', 'kind', 'units', 'operation_key'),
}


def money(value):
    value = Decimal(str(value))
    if not value.is_finite():
        raise ValueError('non-finite decimal')
    return value


def validate(document, label):
    errors, indexes = [], {}
    for table in TABLES:
        rows = document.get(table)
        if not isinstance(rows, list):
            errors.append(f'{label}.{table}: missing array')
            rows = []
        indexes[table] = {}
        for number, row in enumerate(rows, 1):
            prefix = f'{label}.{table}[{number}]'
            if not isinstance(row, dict) or any(row.get(key) in (None, '') for key in REQUIRED[table]):
                errors.append(f'{prefix}: missing required field')
                continue
            key = (str(row['organization_id']), str(row['id']))
            if key in indexes[table]:
                errors.append(f'{prefix}: duplicate stable ID')
            indexes[table][key] = row
    if errors:
        return errors, {}

    def related(row, table, field):
        return indexes[table].get((str(row['organization_id']), str(row[field])))

    for table in ('contacts', 'students', 'enrollments'):
        for row in indexes[table].values():
            if not related(row, 'customers', 'customer_id'):
                errors.append(f'{label}.{table}: missing customer or organization mismatch')
    for row in indexes['guardians'].values():
        student = related(row, 'students', 'student_id')
        contact = related(row, 'contacts', 'contact_id')
        if not student or not contact or student['customer_id'] != row['customer_id'] or contact['customer_id'] != row['customer_id']:
            errors.append(f'{label}.guardians: cross-family or missing relationship')
    for row in indexes['enrollments'].values():
        student = related(row, 'students', 'student_id')
        if not student or student['customer_id'] != row['customer_id']:
            errors.append(f'{label}.enrollments: cross-family or missing student')
        try:
            units, amount = money(row['total_units']), money(row['amount'])
            if units <= 0 or amount < 0 or row['mode'] not in ('HOURS', 'EVENT') or (row['mode'] == 'EVENT' and units != 1):
                raise ValueError('invalid entitlement')
        except (ValueError, InvalidOperation):
            errors.append(f'{label}.enrollments: invalid mode, units or amount')

    if errors:
        return errors, indexes

    balances, receipts, allocated, operations = {}, {}, {}, set()
    for table in ('payments', 'ledger'):
        for row in indexes[table].values():
            enrollment = related(row, 'enrollments', 'enrollment_id')
            if not enrollment:
                errors.append(f'{label}.{table}: missing enrollment or organization mismatch')
                continue
            enrollment_key = (str(row['organization_id']), str(row['enrollment_id']))
            try:
                if table == 'ledger':
                    units = money(row['units'])
                    kind = row['kind']
                    if kind not in ('GRANT', 'DEBIT', 'REVERSAL') or units == 0 or (kind == 'DEBIT') != (units < 0):
                        raise ValueError('invalid ledger sign')
                    op = (*enrollment_key, str(row['operation_key']))
                    if op in operations:
                        errors.append(f'{label}.ledger: duplicate operation key')
                    operations.add(op)
                    balances[enrollment_key] = balances.get(enrollment_key, Decimal(0)) + units
                else:
                    amount, total = money(row['amount']), money(row['receipt_total'])
                    if amount <= 0 or total < 0:
                        raise ValueError('invalid payment')
                    receipt_key = (str(row['organization_id']), str(row['receipt_id']))
                    previous, frozen = receipts.get(receipt_key, (Decimal(0), total))
                    if frozen != total:
                        errors.append(f'{label}.payments: inconsistent receipt snapshot')
                    receipts[receipt_key] = (previous + amount, total)
                    allocated[enrollment_key] = allocated.get(enrollment_key, Decimal(0)) + amount
            except (ValueError, InvalidOperation):
                errors.append(f'{label}.{table}: invalid decimal or ledger sign')
    if any(value < 0 for value in balances.values()):
        errors.append(f'{label}.ledger: negative entitlement balance')
    if any(amount > total for amount, total in receipts.values()):
        errors.append(f'{label}.payments: overallocated receipt')
    if any(value > money(indexes['enrollments'][key]['amount']) for key, value in allocated.items()):
        errors.append(f'{label}.payments: overallocated enrollment')
    return errors, indexes


def reconcile(expected, actual):
    errors, source = validate(expected, 'expected')
    target_errors, target = validate(actual, 'actual')
    errors.extend(target_errors)
    if source and target:
        for table in TABLES:
            for key, row in source[table].items():
                found = target[table].get(key)
                if found is None:
                    errors.append(f'{table}: expected stable ID missing')
                elif any(found.get(field) != value for field, value in row.items()):
                    errors.append(f'{table}: mapped record differs')
            # Extra rows may be post-cutover business events. Report, never delete them.
    extras = {table: len(set(target.get(table, {})) - set(source.get(table, {}))) for table in TABLES}
    return {'passed': not errors, 'errors': errors, 'additional_records': extras,
            'expected_counts': {table: len(expected.get(table, [])) for table in TABLES},
            'actual_counts': {table: len(actual.get(table, [])) for table in TABLES}}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('expected', type=Path)
    parser.add_argument('actual', type=Path)
    parser.add_argument('--report', required=True, type=Path)
    args = parser.parse_args()
    if args.report.resolve() in (args.expected.resolve(), args.actual.resolve()):
        parser.error('report cannot overwrite input')
    raw = [path.read_bytes() for path in (args.expected, args.actual)]
    result = reconcile(*(json.loads(data) for data in raw))
    result['input_sha256'] = [hashlib.sha256(data).hexdigest() for data in raw]
    args.report.write_text(json.dumps(result, indent=2, ensure_ascii=False) + '\n')
    print('PASS' if result['passed'] else 'FAIL')
    return 0 if result['passed'] else 1


if __name__ == '__main__':
    raise SystemExit(main())
