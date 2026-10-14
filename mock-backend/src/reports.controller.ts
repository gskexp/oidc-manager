import { Controller, Get, HttpCode, NotFoundException, Param, Post, StreamableFile } from '@nestjs/common';
import { randomUUID } from 'node:crypto';
import { setTimeout } from 'node:timers/promises';
import { Workbook } from 'exceljs';

const runDelayMs = 800;
const failingReportConfigIds = ['sales-trends'];

interface Column {
  key: string;
  label: string;
  format?: string;
}

interface ReportConfig {
  name: string;
  columns?: Column[];
  [key: string]: unknown;
}

interface ReportResult {
  columns: Column[];
  rows: unknown[][];
  rowCount: number;
  truncated: boolean;
}

const reportConfigs: Record<string, ReportConfig> = {
  'accounts-receivable-aging': {
    reportConfigId: 'accounts-receivable-aging',
    name: 'Accounts Receivable Aging',
    description: 'Outstanding invoices grouped by age.',
    fields: [
      {
        key: 'asAt',
        label: 'As at date',
        type: 'date',
        required: true,
      },
      {
        key: 'buckets',
        label: 'Aging buckets',
        type: 'multiSelect',
        required: true,
        defaultValue: ['0-30', '31-60', '61-90', '90+'],
        options: [
          {
            value: '0-30',
            label: '0\u201330 days',
          },
          {
            value: '31-60',
            label: '31\u201360 days',
          },
          {
            value: '61-90',
            label: '61\u201390 days',
          },
          {
            value: '90+',
            label: 'Over 90 days',
          },
        ],
      },
      {
        key: 'minBalance',
        label: 'Minimum balance',
        type: 'number',
        min: 0,
        step: 0.01,
        placeholder: '0.00',
      },
      {
        key: 'customerId',
        label: 'Customer ID',
        type: 'text',
        pattern: 'C-[0-9]{5}',
        placeholder: 'C-12345',
      },
      {
        key: 'customerName',
        label: 'Customer name',
        type: 'text',
        minLength: 2,
        maxLength: 80,
      },
      {
        key: 'postcode',
        label: 'Postcode',
        type: 'text',
        pattern: '[0-9]{4}',
        maxLength: 4,
      },
    ],
    choices: [
      {
        label: 'Customer',
        helpText: 'Find the customer by ID, or by name and postcode.',
        alternatives: [['customerId'], ['customerName', 'postcode']],
      },
    ],
    columns: [
      {
        key: 'customer_name',
        label: 'Customer',
      },
      {
        key: 'aging_bucket',
        label: 'Age',
      },
      {
        key: 'balance',
        label: 'Balance',
        format: 'decimal',
      },
    ],
  },
  'cash-flow': {
    reportConfigId: 'cash-flow',
    name: 'Cash Flow',
    description: 'Cash inflows and outflows as at a date.',
    fields: [
      {
        key: 'asAt',
        label: 'As at date',
        type: 'date',
        required: true,
      },
      {
        key: 'method',
        label: 'Method',
        type: 'select',
        required: true,
        defaultValue: 'indirect',
        options: [
          {
            value: 'direct',
            label: 'Direct',
          },
          {
            value: 'indirect',
            label: 'Indirect',
          },
        ],
      },
    ],
    columns: [
      {
        key: 'section',
        label: 'Section',
      },
      {
        key: 'line_item',
        label: 'Line item',
      },
      {
        key: 'amount',
        label: 'Amount',
        format: 'decimal',
      },
    ],
  },
  'customer-growth': {
    reportConfigId: 'customer-growth',
    name: 'Customer Growth',
    description: 'New and active customers per period.',
    fields: [
      {
        key: 'period',
        label: 'Reporting period',
        type: 'dateRange',
        required: true,
      },
      {
        key: 'segment',
        label: 'Customer segment',
        type: 'select',
        options: [
          {
            value: 'enterprise',
            label: 'Enterprise',
          },
          {
            value: 'smb',
            label: 'Small & medium business',
          },
          {
            value: 'consumer',
            label: 'Consumer',
          },
        ],
        placeholder: 'All segments',
      },
      {
        key: 'customerName',
        label: 'Customer name contains',
        type: 'text',
        maxLength: 100,
        placeholder: 'e.g. Acme',
        helpText: 'Optional. Filters to customers whose name contains this text.',
      },
    ],
    columns: [
      {
        key: 'signup_month',
        label: 'Month',
        format: 'month',
      },
      {
        key: 'new_customers',
        label: 'New customers',
        format: 'integer',
      },
    ],
  },
  'customer-retention': {
    reportConfigId: 'customer-retention',
    name: 'Customer Retention',
    description: 'Cohort retention for customers acquired in a period.',
    fields: [
      {
        key: 'cohortStart',
        label: 'Cohort start date',
        type: 'date',
        required: true,
        maxDate: '2026-12-31',
      },
      {
        key: 'months',
        label: 'Months to track',
        type: 'number',
        required: true,
        defaultValue: 12,
        min: 1,
        max: 36,
      },
      {
        key: 'regions',
        label: 'Regions',
        type: 'multiSelect',
        options: [
          {
            value: 'north',
            label: 'North',
          },
          {
            value: 'south',
            label: 'South',
          },
          {
            value: 'east',
            label: 'East',
          },
          {
            value: 'west',
            label: 'West',
          },
        ],
        helpText: 'Leave empty to include all regions.',
      },
    ],
    columns: [
      {
        key: 'months_since_acquisition',
        label: 'Months since acquisition',
        format: 'integer',
      },
      {
        key: 'retained_customers',
        label: 'Retained customers',
        format: 'integer',
      },
    ],
  },
  headcount: {
    reportConfigId: 'headcount',
    name: 'Headcount',
    description: 'Employee headcount by department as at a date.',
    fields: [
      {
        key: 'asAt',
        label: 'As at date',
        type: 'date',
        required: true,
      },
      {
        key: 'departments',
        label: 'Departments',
        type: 'multiSelect',
        minSelected: 1,
        defaultValue: ['finance', 'sales', 'operations', 'hr', 'it'],
        options: [
          {
            value: 'finance',
            label: 'Finance',
          },
          {
            value: 'sales',
            label: 'Sales',
          },
          {
            value: 'operations',
            label: 'Operations',
          },
          {
            value: 'hr',
            label: 'Human Resources',
          },
          {
            value: 'it',
            label: 'Information Technology',
          },
        ],
      },
      {
        key: 'employmentType',
        label: 'Employment type',
        type: 'select',
        options: [
          {
            value: 'permanent',
            label: 'Permanent',
          },
          {
            value: 'contract',
            label: 'Contract',
          },
          {
            value: 'casual',
            label: 'Casual',
          },
        ],
        placeholder: 'All types',
      },
    ],
    columns: [
      {
        key: 'department',
        label: 'Department',
      },
      {
        key: 'headcount',
        label: 'Headcount',
        format: 'integer',
      },
    ],
  },
  'inventory-levels': {
    reportConfigId: 'inventory-levels',
    name: 'Inventory Levels',
    description: 'Stock on hand by warehouse and SKU.',
    fields: [
      {
        key: 'asAt',
        label: 'As at date',
        type: 'date',
      },
      {
        key: 'warehouses',
        label: 'Warehouses',
        type: 'multiSelect',
        minSelected: 1,
        maxSelected: 3,
        options: [
          {
            value: 'syd',
            label: 'Sydney',
          },
          {
            value: 'mel',
            label: 'Melbourne',
          },
          {
            value: 'bne',
            label: 'Brisbane',
          },
          {
            value: 'per',
            label: 'Perth',
          },
        ],
        helpText: 'Select up to 3 warehouses.',
      },
      {
        key: 'skuPrefix',
        label: 'SKU prefix',
        type: 'text',
        maxLength: 12,
        pattern: '[A-Z0-9-]+',
        helpText: 'Uppercase letters, digits and hyphens only.',
      },
      {
        key: 'belowReorderOnly',
        label: 'Stock filter',
        type: 'select',
        defaultValue: 'all',
        options: [
          {
            value: 'all',
            label: 'All items',
          },
          {
            value: 'below-reorder',
            label: 'Below reorder point only',
          },
        ],
      },
    ],
    columns: [
      {
        key: 'warehouse_code',
        label: 'Warehouse',
      },
      {
        key: 'sku',
        label: 'SKU',
      },
      {
        key: 'quantity_on_hand',
        label: 'On hand',
        format: 'integer',
      },
      {
        key: 'reorder_point',
        label: 'Reorder point',
        format: 'integer',
      },
    ],
  },
  'leave-balances': {
    reportConfigId: 'leave-balances',
    name: 'Leave Balances',
    description: 'Accrued and taken leave by employee.',
    fields: [
      {
        key: 'employeeId',
        label: 'Employee ID',
        type: 'text',
        pattern: 'E[0-9]{5}',
        placeholder: 'E12345',
        helpText: 'Leave empty to include all employees.',
      },
      {
        key: 'department',
        label: 'Department',
        type: 'select',
        options: [
          {
            value: 'finance',
            label: 'Finance',
          },
          {
            value: 'sales',
            label: 'Sales',
          },
          {
            value: 'operations',
            label: 'Operations',
          },
          {
            value: 'hr',
            label: 'Human Resources',
          },
          {
            value: 'it',
            label: 'Information Technology',
          },
        ],
        placeholder: 'All departments',
      },
      {
        key: 'minDays',
        label: 'Minimum balance (days)',
        type: 'number',
        min: 0,
        max: 365,
        step: 0.5,
      },
    ],
    columns: [
      {
        key: 'employee_id',
        label: 'Employee ID',
      },
      {
        key: 'employee_name',
        label: 'Name',
      },
      {
        key: 'department',
        label: 'Department',
      },
      {
        key: 'accrued_days',
        label: 'Accrued (days)',
        format: 'decimal',
      },
      {
        key: 'taken_days',
        label: 'Taken (days)',
        format: 'decimal',
      },
      {
        key: 'balance_days',
        label: 'Balance (days)',
        format: 'decimal',
      },
    ],
  },
  'monthly-sales': {
    reportConfigId: 'monthly-sales',
    name: 'Monthly Sales',
    description: 'Sales totals by month for the selected period and regions.',
    fields: [
      {
        key: 'period',
        label: 'Reporting period',
        type: 'dateRange',
        required: true,
      },
      {
        key: 'regions',
        label: 'Regions',
        type: 'multiSelect',
        minSelected: 1,
        options: [
          {
            value: 'north',
            label: 'North',
          },
          {
            value: 'south',
            label: 'South',
          },
          {
            value: 'east',
            label: 'East',
          },
          {
            value: 'west',
            label: 'West',
          },
        ],
      },
      {
        key: 'currency',
        label: 'Currency',
        type: 'select',
        defaultValue: 'USD',
        options: [
          {
            value: 'USD',
            label: 'US Dollar',
          },
          {
            value: 'EUR',
            label: 'Euro',
          },
        ],
      },
      {
        key: 'minAmount',
        label: 'Minimum order amount',
        type: 'number',
        min: 0,
      },
    ],
    columns: [
      {
        key: 'sales_month',
        label: 'Month',
        format: 'month',
      },
      {
        key: 'region',
        label: 'Region',
      },
      {
        key: 'total_amount',
        label: 'Total sales',
        format: 'decimal',
      },
      {
        key: 'order_count',
        label: 'Orders',
        format: 'integer',
      },
    ],
  },
  'order-fulfilment': {
    reportConfigId: 'order-fulfilment',
    name: 'Order Fulfilment',
    description: 'Order cycle times and on-time delivery rates.',
    fields: [
      {
        key: 'period',
        label: 'Order date range',
        type: 'dateRange',
        required: true,
        minDate: '2024-01-01',
        maxDate: '2026-12-31',
      },
      {
        key: 'warehouses',
        label: 'Warehouses',
        type: 'multiSelect',
        options: [
          {
            value: 'syd',
            label: 'Sydney',
          },
          {
            value: 'mel',
            label: 'Melbourne',
          },
          {
            value: 'bne',
            label: 'Brisbane',
          },
          {
            value: 'per',
            label: 'Perth',
          },
        ],
      },
      {
        key: 'slaHours',
        label: 'SLA threshold (hours)',
        type: 'number',
        defaultValue: 48,
        min: 1,
        max: 720,
      },
    ],
    columns: [
      {
        key: 'warehouse_code',
        label: 'Warehouse',
      },
      {
        key: 'orders',
        label: 'Orders',
        format: 'integer',
      },
      {
        key: 'avg_hours_to_ship',
        label: 'Avg. hours to ship',
        format: 'decimal',
      },
      {
        key: 'on_time_rate',
        label: 'On-time rate',
        format: 'percent',
      },
    ],
  },
  'profit-and-loss': {
    reportConfigId: 'profit-and-loss',
    name: 'Profit and Loss',
    description: 'Income statement for a period by cost centre.',
    fields: [
      {
        key: 'period',
        label: 'Financial period',
        type: 'dateRange',
        required: true,
      },
      {
        key: 'costCentre',
        label: 'Cost centre code',
        type: 'text',
        pattern: 'CC-[0-9]{4}',
        placeholder: 'CC-1234',
        helpText: 'Format: CC- followed by four digits. Leave empty for all cost centres.',
      },
      {
        key: 'currency',
        label: 'Currency',
        type: 'select',
        defaultValue: 'USD',
        options: [
          {
            value: 'USD',
            label: 'US Dollar',
          },
          {
            value: 'EUR',
            label: 'Euro',
          },
        ],
      },
    ],
    columns: [
      {
        key: 'account_type',
        label: 'Account type',
      },
      {
        key: 'account_name',
        label: 'Account',
      },
      {
        key: 'amount',
        label: 'Amount',
        format: 'decimal',
      },
    ],
  },
  'sales-by-region': {
    reportConfigId: 'sales-by-region',
    name: 'Sales by Region',
    description: 'Compare sales across regions for a single period.',
    fields: [
      {
        key: 'period',
        label: 'Reporting period',
        type: 'dateRange',
        required: true,
        defaultValue: {
          from: '2026-01-01',
          to: '2026-06-30',
        },
      },
      {
        key: 'regions',
        label: 'Regions',
        type: 'multiSelect',
        minSelected: 2,
        maxSelected: 4,
        defaultValue: ['north', 'south'],
        options: [
          {
            value: 'north',
            label: 'North',
          },
          {
            value: 'south',
            label: 'South',
          },
          {
            value: 'east',
            label: 'East',
          },
          {
            value: 'west',
            label: 'West',
          },
        ],
        helpText: 'Choose between 2 and 4 regions to compare.',
      },
      {
        key: 'groupBy',
        label: 'Group by',
        type: 'select',
        required: true,
        defaultValue: 'month',
        options: [
          {
            value: 'week',
            label: 'Week',
          },
          {
            value: 'month',
            label: 'Month',
          },
          {
            value: 'quarter',
            label: 'Quarter',
          },
        ],
      },
    ],
    columns: [
      {
        key: 'bucket',
        label: 'Period',
        format: 'date',
      },
      {
        key: 'region',
        label: 'Region',
      },
      {
        key: 'total_amount',
        label: 'Total sales',
        format: 'decimal',
      },
    ],
  },
  'sales-trends': {
    reportConfigId: 'sales-trends',
    name: 'Sales Trends',
    description: 'Rolling trend lines for product lines over time.',
    fields: [
      {
        key: 'period',
        label: 'Trend period',
        type: 'dateRange',
        required: true,
        minDate: '2020-01-01',
      },
      {
        key: 'productLine',
        label: 'Product line',
        type: 'select',
        required: true,
        placeholder: 'Select a product line',
        options: [
          {
            value: 'hardware',
            label: 'Hardware',
          },
          {
            value: 'software',
            label: 'Software',
          },
          {
            value: 'services',
            label: 'Services',
          },
        ],
      },
      {
        key: 'windowMonths',
        label: 'Rolling window (months)',
        type: 'number',
        required: true,
        defaultValue: 3,
        min: 1,
        max: 24,
        step: 1,
      },
    ],
    columns: [
      {
        key: 'sales_month',
        label: 'Month',
        format: 'month',
      },
      {
        key: 'total_amount',
        label: 'Sales',
        format: 'decimal',
      },
      {
        key: 'rolling_avg',
        label: 'Rolling average',
        format: 'decimal',
      },
    ],
  },
};

const reportResults: Record<string, ReportResult> = {
  headcount: {
    columns: [
      {
        key: 'department',
        label: 'Department',
      },
      {
        key: 'headcount',
        label: 'Headcount',
        format: 'integer',
      },
    ],
    rows: [
      ['finance', 23],
      ['hr', 12],
      ['it', 31],
      ['operations', 56],
      ['sales', 40],
    ],
    rowCount: 5,
    truncated: false,
  },
  'inventory-levels': {
    columns: [
      {
        key: 'warehouse_code',
        label: 'Warehouse',
      },
      {
        key: 'sku',
        label: 'SKU',
      },
      {
        key: 'quantity_on_hand',
        label: 'On hand',
        format: 'integer',
      },
      {
        key: 'reorder_point',
        label: 'Reorder point',
        format: 'integer',
      },
    ],
    rows: [
      ['mel', 'AC-0001', 31, 50],
      ['mel', 'AC-0002', 28, 20],
      ['mel', 'AC-0003', 156, 50],
      ['mel', 'AC-0004', 30, 20],
      ['mel', 'AC-0005', 38, 20],
      ['mel', 'HW-0001', 2, 20],
      ['mel', 'HW-0002', 44, 20],
      ['mel', 'HW-0003', 130, 100],
      ['mel', 'HW-0004', 148, 100],
      ['mel', 'HW-0005', 28, 20],
      ['mel', 'PK-0001', 126, 50],
      ['mel', 'PK-0002', 228, 100],
      ['mel', 'PK-0003', 51, 20],
      ['mel', 'PK-0004', 259, 100],
      ['mel', 'PK-0005', 176, 100],
      ['mel', 'SV-0001', 61, 50],
      ['mel', 'SV-0002', 24, 20],
      ['mel', 'SV-0003', 129, 100],
      ['mel', 'SV-0004', 191, 100],
      ['mel', 'SV-0005', 8, 20],
      ['mel', 'SW-0001', 10, 20],
      ['mel', 'SW-0002', 170, 100],
      ['mel', 'SW-0003', 40, 20],
      ['mel', 'SW-0004', 89, 50],
      ['mel', 'SW-0005', 186, 100],
      ['syd', 'AC-0001', 57, 50],
      ['syd', 'AC-0002', 36, 50],
      ['syd', 'AC-0003', 93, 50],
      ['syd', 'AC-0004', 139, 100],
      ['syd', 'AC-0005', 100, 50],
      ['syd', 'HW-0001', 12, 100],
      ['syd', 'HW-0002', 211, 100],
      ['syd', 'HW-0003', 70, 50],
      ['syd', 'HW-0004', 203, 100],
      ['syd', 'HW-0005', 78, 50],
      ['syd', 'PK-0001', 55, 20],
      ['syd', 'PK-0002', 259, 100],
      ['syd', 'PK-0003', 49, 20],
      ['syd', 'PK-0004', 57, 50],
      ['syd', 'PK-0005', 44, 20],
      ['syd', 'SV-0001', 297, 100],
      ['syd', 'SV-0002', 129, 100],
      ['syd', 'SV-0003', 26, 20],
      ['syd', 'SV-0004', 46, 50],
      ['syd', 'SV-0005', 181, 100],
      ['syd', 'SW-0001', 116, 100],
      ['syd', 'SW-0002', 75, 50],
      ['syd', 'SW-0003', 294, 100],
      ['syd', 'SW-0004', 125, 100],
      ['syd', 'SW-0005', 32, 20],
    ],
    rowCount: 50,
    truncated: false,
  },
  'monthly-sales': {
    columns: [
      {
        key: 'sales_month',
        label: 'Month',
        format: 'month',
      },
      {
        key: 'region',
        label: 'Region',
      },
      {
        key: 'total_amount',
        label: 'Total sales',
        format: 'decimal',
      },
      {
        key: 'order_count',
        label: 'Orders',
        format: 'integer',
      },
    ],
    rows: [
      ['2026-01-01', 'east', 79161.55, 39],
      ['2026-01-01', 'north', 82396.02, 48],
      ['2026-01-01', 'south', 51425.75, 46],
      ['2026-01-01', 'west', 41787.69, 35],
      ['2026-02-01', 'east', 81835.14, 38],
      ['2026-02-01', 'north', 71269.83, 41],
      ['2026-02-01', 'south', 62647.7, 42],
      ['2026-02-01', 'west', 44945.79, 24],
      ['2026-03-01', 'east', 93486.06, 41],
      ['2026-03-01', 'north', 74355.24, 35],
      ['2026-03-01', 'south', 55157.27, 34],
      ['2026-03-01', 'west', 70923.48, 41],
      ['2026-04-01', 'east', 84730.65, 37],
      ['2026-04-01', 'north', 115332.35, 51],
      ['2026-04-01', 'south', 63616.39, 37],
      ['2026-04-01', 'west', 85729.27, 39],
      ['2026-05-01', 'east', 86115.59, 26],
      ['2026-05-01', 'north', 68825.67, 38],
      ['2026-05-01', 'south', 77995.51, 33],
      ['2026-05-01', 'west', 96507.71, 40],
      ['2026-06-01', 'east', 153497.17, 45],
      ['2026-06-01', 'north', 89165.74, 39],
      ['2026-06-01', 'south', 96265.62, 37],
      ['2026-06-01', 'west', 84656.87, 35],
    ],
    rowCount: 24,
    truncated: false,
  },
  'profit-and-loss': {
    columns: [
      {
        key: 'account_type',
        label: 'Account type',
      },
      {
        key: 'account_name',
        label: 'Account',
      },
      {
        key: 'amount',
        label: 'Amount',
        format: 'decimal',
      },
    ],
    rows: [
      ['Cost of sales', 'Freight', -35938.46],
      ['Cost of sales', 'Materials', -270974.86],
      ['Expense', 'Marketing', -40411.07],
      ['Expense', 'Rent', -48000],
      ['Expense', 'Salaries', -218823.19],
      ['Expense', 'Software licences', -15832.59],
      ['Revenue', 'Product sales', 688885.42],
      ['Revenue', 'Service fees', 241643.21],
    ],
    rowCount: 8,
    truncated: false,
  },
  'sales-trends': {
    columns: [
      {
        key: 'sales_month',
        label: 'Month',
        format: 'month',
      },
      {
        key: 'total_amount',
        label: 'Sales',
        format: 'decimal',
      },
      {
        key: 'rolling_avg',
        label: 'Rolling average',
        format: 'decimal',
      },
    ],
    rows: [
      ['2025-01-01', 63202.34, 63202.34],
      ['2025-02-01', 75366.1, 69284.22],
      ['2025-03-01', 78123.9, 72230.78],
      ['2025-04-01', 78317.01, 73752.3375],
      ['2025-05-01', 108067.51, 84968.63],
      ['2025-06-01', 157200.92, 105427.335],
      ['2025-07-01', 101252.83, 111209.5675],
      ['2025-08-01', 101758.93, 117070.0475],
      ['2025-09-01', 88176.37, 112097.2625],
      ['2025-10-01', 76680.96, 91967.2725],
      ['2025-11-01', 73150.96, 84941.805],
      ['2025-12-01', 88348.06, 81589.0875],
      ['2026-01-01', 83884.16, 80516.035],
      ['2026-02-01', 99747.97, 86282.7875],
      ['2026-03-01', 119831.2, 97952.8475],
      ['2026-04-01', 150415.35, 113469.67],
      ['2026-05-01', 155588.97, 131395.8725],
      ['2026-06-01', 98976.39, 131202.9775],
      ['2026-07-01', 172517.15, 144374.465],
      ['2026-08-01', 152941.18, 145005.9225],
      ['2026-09-01', 90956.39, 128847.7775],
    ],
    rowCount: 21,
    truncated: false,
  },
};

@Controller()
export class ReportsController {
  @Get('report-configs/:id')
  reportConfig(@Param('id') id: string) {
    return this.config(id);
  }

  @Post('reports/:id/run')
  @HttpCode(200)
  async run(@Param('id') id: string) {
    this.config(id);
    await setTimeout(runDelayMs);
    if (failingReportConfigIds.includes(id)) {
      return { runId: randomUUID(), status: 'failed', message: 'Simulated failure from the mock backend.' };
    }
    return { ...this.result(id), runId: randomUUID(), status: 'completed' };
  }

  @Post('reports/:id/export')
  @HttpCode(200)
  async export(@Param('id') id: string) {
    const config = this.config(id);
    const result = this.result(id);
    const columns = result.columns.length ? result.columns : (config.columns ?? []);

    const workbook = new Workbook();
    const sheet = workbook.addWorksheet(config.name.slice(0, 31));
    sheet.columns = columns.map((column) => ({ header: column.label, key: column.key, width: 20 }));
    sheet.getRow(1).font = { bold: true };
    sheet.addRows(result.rows);

    const buffer = await workbook.xlsx.writeBuffer();
    return new StreamableFile(Buffer.from(buffer), {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      disposition: `attachment; filename="${id}.xlsx"`,
    });
  }

  private config(id: string): ReportConfig {
    if (!Object.hasOwn(reportConfigs, id)) throw new NotFoundException();
    return reportConfigs[id];
  }

  private result(id: string): ReportResult {
    const empty: ReportResult = { columns: [], rows: [], rowCount: 0, truncated: false };
    return Object.hasOwn(reportResults, id) ? reportResults[id] : empty;
  }
}
