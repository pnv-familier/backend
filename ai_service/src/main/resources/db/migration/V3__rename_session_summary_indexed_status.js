// MongoDB Migration Script
// Version: V3
// Description: Rename 'summaryIndexedStatus' → 'qdrantSyncStatus' in chat_sessions collection
// Run this once before deploying the ai_service build that renames ChatSession.summaryIndexedStatus → qdrantSyncStatus
//
// Usage: run in MongoDB shell or mongosh
//   mongosh <connection_string> --file V3__rename_session_summary_indexed_status.js

db = db.getSiblingDB('familier_ai');

var result = db.chat_sessions.updateMany(
  { summaryIndexedStatus: { $exists: true } },
  { $rename: { "summaryIndexedStatus": "qdrantSyncStatus" } }
);

print("Migration V3 complete:");
print("  Matched:  " + result.matchedCount);
print("  Modified: " + result.modifiedCount);
