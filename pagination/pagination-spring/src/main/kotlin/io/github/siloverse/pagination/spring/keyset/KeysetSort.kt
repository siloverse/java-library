package io.github.siloverse.pagination.spring.keyset

import io.github.siloverse.pagination.core.keyset.KeysetKeys
import io.github.siloverse.pagination.core.sort.Direction
import org.springframework.data.domain.Sort

/** The Spring Data sort for these keys: same columns, same order, same directions as the cursor. */
fun KeysetKeys.toSort(): Sort =
    Sort.by(keys.map { Sort.Order(if (it.direction == Direction.ASC) Sort.Direction.ASC else Sort.Direction.DESC, it.property) })
