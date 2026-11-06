package com.byteharvest.master.model;

/**
 * Outcome of comparing one node's stored chunks with the master's metadata.
 *
 * @param adopted        chunks whose replica record was re-created because the node still holds a valid copy
 * @param surplusRemoved copies deleted because the chunk already has enough healthy replicas elsewhere
 * @param corruptRemoved copies deleted because their checksum did not match
 * @param unknownKept    chunks the master has no record of; never deleted automatically
 * @param skipped        chunks that could not be checked this time (the node or network failed mid-way)
 */
public record ReconcileResult(int adopted, int surplusRemoved, int corruptRemoved, int unknownKept, int skipped) {

    public static final ReconcileResult EMPTY = new ReconcileResult(0, 0, 0, 0, 0);

    public boolean hasChanges() {
        return adopted > 0 || surplusRemoved > 0 || corruptRemoved > 0 || unknownKept > 0 || skipped > 0;
    }
}
