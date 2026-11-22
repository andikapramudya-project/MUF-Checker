import { analyzeDueDate } from "@muf/inquiry-core";
import { CHANNEL, type CheckRecord, type QueueItem } from "@muf/shared";

export async function simulate(items: QueueItem[], onItem: (item: QueueItem, record: CheckRecord) => void, isStopped: () => boolean) {
  for (let index = 0; index < items.length; index += 1) {
    if (isStopped()) return;
    const item = items[index];
    if (!item) continue;
    await new Promise((resolve) => setTimeout(resolve, 350));
    const date = new Date(Date.UTC(item.period.year, item.period.month, 4));
    const dueDate = `${String(date.getUTCDate()).padStart(2, "0")}-${String(date.getUTCMonth() + 1).padStart(2, "0")}-${date.getUTCFullYear()}`;
    const createdAt = new Date().toISOString();
    const record: CheckRecord = {
      id: crypto.randomUUID(),
      contractNumber: item.contractNumber,
      maskedContract: item.maskedContract,
      period: item.period,
      inquiry: {
        maskedContract: item.maskedContract,
        dueDate,
        installmentCurrent: 12,
        installmentTotal: 36,
        inquiryAt: createdAt,
        source: CHANNEL,
        simulation: true
      },
      analysis: analyzeDueDate(item.period, dueDate),
      queueStatus: "SELESAI",
      createdAt
    };
    onItem(item, record);
  }
}
