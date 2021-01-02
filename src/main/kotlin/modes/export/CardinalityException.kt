/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * Copyright (c) 2021
 */

package net.nprod.wikidataLotusExporter.modes.export

/**
 * Thrown when an object that should exist in a specific number or range is out of specification
 */
class CardinalityException(override val message: String?) : RuntimeException()
