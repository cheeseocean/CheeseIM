#!/usr/bin/env node
import { readFileSync } from 'node:fs';

const summary = JSON.parse(readFileSync(process.argv[2] || 'summary.json', 'utf8'));
const count = (name) => summary.metrics?.[name]?.values?.count || 0;
const sent = count('im_chat_sent');
const accepted = count('im_broker_accepted');
const received = count('im_received_unique');
const duplicates = count('im_received_duplicate');
const deliveryAcks = count('im_delivery_ack_sent');
const minRatio = Number(process.env.MIN_DELIVERY_RATIO || '0.999');
const acceptedRatio = sent === 0 ? 0 : accepted / sent;
const deliveryRatio = accepted === 0 ? 0 : received / accepted;
const ackRatio = received === 0 ? 0 : deliveryAcks / received;

console.log(JSON.stringify({ sent, accepted, received, duplicates, deliveryAcks,
  acceptedRatio, deliveryRatio, ackRatio, minRatio }, null, 2));

if (duplicates !== 0 || acceptedRatio < minRatio || deliveryRatio < minRatio || ackRatio < minRatio) {
  console.error('容量/chaos 正确性阈值未通过');
  process.exit(1);
}
