package com.alsoug.keswa.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * 1 → 2: adds `app_setting`.
 *
 * Purely additive, so nothing existing is touched — which is the point. The DDL is copied verbatim
 * from Room's exported `2.json` rather than written by hand: Room validates the schema's identity
 * hash on open, and a column declared even slightly differently fails at startup.
 */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `app_setting` " +
                "(`key` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`key`))",
        )
    }
}

/**
 * 2 → 3: adds `app_user`.
 *
 * Additive again, and deliberately so — the shop's trading history predates its user accounts, and
 * an upgrade must never put that at risk to introduce a login. DDL copied verbatim from Room's
 * exported `3.json`, index included: Room validates the schema's identity hash on open, so a
 * hand-written approximation fails at startup rather than at review.
 */
val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `app_user` (" +
                "`id` TEXT NOT NULL, `username` TEXT NOT NULL, `displayName` TEXT NOT NULL, " +
                "`displayNameAr` TEXT NOT NULL, `role` TEXT NOT NULL, `secretHash` TEXT NOT NULL, " +
                "`secretSalt` TEXT NOT NULL, `secretKind` TEXT NOT NULL, `isActive` INTEGER NOT NULL, " +
                "`mustChangeSecret` INTEGER NOT NULL, `failedAttempts` INTEGER NOT NULL, " +
                "`lockedUntil` INTEGER, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))",
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_app_user_username` ON `app_user` (`username`)",
        )
    }
}

/**
 * 3 → 4: adds the sell flow — `sale`, `sale_line`, `payment`, `shift`, `held_sale`,
 * `held_sale_line`.
 *
 * The largest migration so far and still purely additive: this is the first release that could
 * meet a database with real trading history in it, and an upgrade must never put that at risk to
 * introduce a till. DDL copied verbatim from Room's exported `4.json`, indices included — Room
 * validates the schema's identity hash on open, so a hand-written approximation fails at startup
 * rather than at review.
 */
val MIGRATION_3_4: Migration = object : Migration(3, 4) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `sale` (`id` TEXT NOT NULL, `receiptNumber` INTEGER NOT NULL, " +
                "`locationId` TEXT NOT NULL, `priceListId` TEXT NOT NULL, `userId` TEXT NOT NULL, " +
                "`shiftId` TEXT, `status` TEXT NOT NULL, `subtotalPiastres` INTEGER NOT NULL, " +
                "`discountPiastres` INTEGER NOT NULL, `taxPiastres` INTEGER NOT NULL, `totalPiastres` " +
                "INTEGER NOT NULL, `tenderedPiastres` INTEGER NOT NULL, `changePiastres` INTEGER NOT " +
                "NULL, `occurredAt` INTEGER NOT NULL, `voidedAt` INTEGER, `voidedByUserId` TEXT, " +
                "`voidReason` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`locationId`) REFERENCES " +
                "`location`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_sale_receiptNumber` ON `sale` (`receiptNumber`)",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_sale_occurredAt` ON `sale` (`occurredAt`)",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_sale_shiftId` ON `sale` (`shiftId`)",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_sale_userId` ON `sale` (`userId`)",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_sale_locationId` ON `sale` (`locationId`)",
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `sale_line` (`id` TEXT NOT NULL, `saleId` TEXT NOT NULL, " +
                "`lineNumber` INTEGER NOT NULL, `variantId` TEXT NOT NULL, `description` TEXT NOT NULL, " +
                "`descriptionAr` TEXT NOT NULL, `quantity` INTEGER NOT NULL, `unitPricePiastres` INTEGER " +
                "NOT NULL, `lineDiscountPiastres` INTEGER NOT NULL, `orderDiscountPiastres` INTEGER NOT " +
                "NULL, `lineTotalPiastres` INTEGER NOT NULL, `taxPiastres` INTEGER NOT NULL, " +
                "`unitCostPiastres` INTEGER NOT NULL, `authorisedByUserId` TEXT, PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`saleId`) REFERENCES `sale`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , " +
                "FOREIGN KEY(`variantId`) REFERENCES `variant`(`id`) ON UPDATE NO ACTION ON DELETE " +
                "RESTRICT )",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_sale_line_saleId` ON `sale_line` (`saleId`)",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_sale_line_variantId` ON `sale_line` (`variantId`)",
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `payment` (`id` TEXT NOT NULL, `saleId` TEXT NOT NULL, " +
                "`method` TEXT NOT NULL, `amountPiastres` INTEGER NOT NULL, `tenderedPiastres` INTEGER " +
                "NOT NULL, `reference` TEXT, `occurredAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN " +
                "KEY(`saleId`) REFERENCES `sale`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_payment_saleId` ON `payment` (`saleId`)",
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `shift` (`id` TEXT NOT NULL, `locationId` TEXT NOT NULL, " +
                "`openedByUserId` TEXT NOT NULL, `openedAt` INTEGER NOT NULL, `openingFloatPiastres` " +
                "INTEGER NOT NULL, `closedAt` INTEGER, `closedByUserId` TEXT, `countedCashPiastres` " +
                "INTEGER, `expectedCashPiastres` INTEGER, `note` TEXT, PRIMARY KEY(`id`), FOREIGN " +
                "KEY(`locationId`) REFERENCES `location`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_shift_locationId` ON `shift` (`locationId`)",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_shift_openedByUserId` ON `shift` (`openedByUserId`)",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_shift_openedAt` ON `shift` (`openedAt`)",
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `held_sale` (`id` TEXT NOT NULL, `label` TEXT NOT NULL, " +
                "`locationId` TEXT NOT NULL, `userId` TEXT NOT NULL, `heldAt` INTEGER NOT NULL, PRIMARY " +
                "KEY(`id`), FOREIGN KEY(`locationId`) REFERENCES `location`(`id`) ON UPDATE NO ACTION ON " +
                "DELETE RESTRICT )",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_held_sale_locationId` ON `held_sale` (`locationId`)",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_held_sale_heldAt` ON `held_sale` (`heldAt`)",
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `held_sale_line` (`id` TEXT NOT NULL, `heldSaleId` TEXT NOT " +
                "NULL, `lineNumber` INTEGER NOT NULL, `variantId` TEXT NOT NULL, `quantity` INTEGER NOT " +
                "NULL, `unitPricePiastres` INTEGER NOT NULL, `lineDiscountPiastres` INTEGER NOT NULL, " +
                "`authorisedByUserId` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`heldSaleId`) REFERENCES " +
                "`held_sale`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`variantId`) " +
                "REFERENCES `variant`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_held_sale_line_heldSaleId` ON `held_sale_line` " +
                "(`heldSaleId`)",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_held_sale_line_variantId` ON `held_sale_line` " +
                "(`variantId`)",
        )
    }
}

/**
 * 4 → 5: adds receiving and counting, and gives the ledger a cost basis.
 *
 * The first migration that touches an existing table. `ALTER TABLE ... ADD COLUMN` with no default
 * is the one alteration SQLite does cheaply and safely: existing rows get `NULL`, which is the
 * honest answer — movements written before Phase 6 genuinely had no cost recorded and no reason in
 * words. Nothing is rewritten, so a shop with a year of trading pays nothing for the upgrade.
 *
 * DDL copied verbatim from Room's exported `5.json`.
 */
val MIGRATION_4_5: Migration = object : Migration(4, 5) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE `stock_movement` ADD COLUMN `unitCostPiastres` INTEGER",
        )
        connection.execSQL(
            "ALTER TABLE `stock_movement` ADD COLUMN `note` TEXT",
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `stock_receipt` (`id` TEXT NOT NULL, `reference` TEXT NOT " +
                "NULL, `supplierName` TEXT NOT NULL, `locationId` TEXT NOT NULL, `status` TEXT NOT " +
                "NULL, `note` TEXT, `createdAt` INTEGER NOT NULL, `createdByUserId` TEXT NOT NULL, " +
                "`postedAt` INTEGER, `postedByUserId` TEXT, `totalCostPiastres` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`), FOREIGN KEY(`locationId`) REFERENCES `location`(`id`) ON UPDATE " +
                "NO ACTION ON DELETE RESTRICT )",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_stock_receipt_locationId` ON `stock_receipt` " +
                "(`locationId`)",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_stock_receipt_createdAt` ON `stock_receipt` " +
                "(`createdAt`)",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_stock_receipt_status` ON `stock_receipt` " +
                "(`status`)",
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `stock_receipt_line` (`id` TEXT NOT NULL, `receiptId` " +
                "TEXT NOT NULL, `lineNumber` INTEGER NOT NULL, `variantId` TEXT NOT NULL, `quantity` " +
                "INTEGER NOT NULL, `unitCostPiastres` INTEGER NOT NULL, `lineTotalPiastres` INTEGER " +
                "NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`receiptId`) REFERENCES " +
                "`stock_receipt`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN " +
                "KEY(`variantId`) REFERENCES `variant`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_stock_receipt_line_receiptId` ON " +
                "`stock_receipt_line` (`receiptId`)",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_stock_receipt_line_variantId` ON " +
                "`stock_receipt_line` (`variantId`)",
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `stock_count` (`id` TEXT NOT NULL, `locationId` TEXT NOT " +
                "NULL, `status` TEXT NOT NULL, `note` TEXT, `startedAt` INTEGER NOT NULL, " +
                "`startedByUserId` TEXT NOT NULL, `postedAt` INTEGER, `postedByUserId` TEXT, PRIMARY " +
                "KEY(`id`), FOREIGN KEY(`locationId`) REFERENCES `location`(`id`) ON UPDATE NO ACTION " +
                "ON DELETE RESTRICT )",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_stock_count_locationId` ON `stock_count` " +
                "(`locationId`)",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_stock_count_startedAt` ON `stock_count` " +
                "(`startedAt`)",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_stock_count_status` ON `stock_count` (`status`)",
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `stock_count_line` (`id` TEXT NOT NULL, `countId` TEXT " +
                "NOT NULL, `lineNumber` INTEGER NOT NULL, `variantId` TEXT NOT NULL, " +
                "`countedQuantity` INTEGER NOT NULL, `expectedQuantity` INTEGER, `varianceQuantity` " +
                "INTEGER, PRIMARY KEY(`id`), FOREIGN KEY(`countId`) REFERENCES `stock_count`(`id`) ON " +
                "UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`variantId`) REFERENCES " +
                "`variant`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_stock_count_line_countId` ON `stock_count_line` " +
                "(`countId`)",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_stock_count_line_variantId` ON `stock_count_line` " +
                "(`variantId`)",
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_stock_count_line_countId_variantId` ON " +
                "`stock_count_line` (`countId`, `variantId`)",
        )
    }
}

/**
 * Every migration this database has ever shipped, in order.
 *
 * KD-002: the local database is the source of truth until sync arrives, so
 * `fallbackToDestructiveMigration` is a blocker and each version bump adds a hand-written entry
 * here plus a test that seeds the previous schema, migrates, and asserts the rows survived intact.
 */
val ALL_MIGRATIONS: Array<Migration> = arrayOf(
    MIGRATION_1_2,
    MIGRATION_2_3,
    MIGRATION_3_4,
    MIGRATION_4_5,
)
