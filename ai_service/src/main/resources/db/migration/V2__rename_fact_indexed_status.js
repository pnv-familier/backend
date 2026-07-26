// MongoDB Migration Script
// Version: V2
// Description: Rename 'indexedStatus' → 'qdrantSyncStatus' in UserContext.facts array elements
// Run this once before deploying the ai_service build that renames UserContext.Fact.indexedStatus → qdrantSyncStatus
//
// Usage: run in MongoDB shell or mongosh
//   mongosh <connection_string> --file V2__rename_fact_indexed_status.js
//
// Note: MongoDB $rename does not work on array subdocument fields.
//       We use $set + $unset on each document manually via cursor iteration.

db = db.getSiblingDB('familier_ai');

var count = 0;
db.user_contexts.find({ "facts.indexedStatus": { $exists: true } }).forEach(function(doc) {
  var updatedFacts = doc.facts.map(function(fact) {
    if (fact.indexedStatus !== undefined) {
      fact.qdrantSyncStatus = fact.indexedStatus;
      delete fact.indexedStatus;
    }
    return fact;
  });
  db.user_contexts.updateOne(
    { _id: doc._id },
    { $set: { facts: updatedFacts } }
  );
  count++;
});

print("Migration V2 complete:");
print("  Documents updated: " + count);
