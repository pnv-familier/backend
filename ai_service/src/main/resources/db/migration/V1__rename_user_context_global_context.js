// MongoDB Migration Script
// Version: V1
// Description: Rename 'globalContext' field to 'userOverview' in user_contexts collection
// Run this once before deploying the ai_service build that renames UserContext.globalContext → userOverview
//
// Usage: run in MongoDB shell or mongosh
//   mongosh <connection_string> --file V1__rename_user_context_global_context.js

db = db.getSiblingDB('familier_ai');

var result = db.user_contexts.updateMany(
  { globalContext: { $exists: true } },
  { $rename: { "globalContext": "userOverview" } }
);

print("Migration V1 complete:");
print("  Matched:  " + result.matchedCount);
print("  Modified: " + result.modifiedCount);
