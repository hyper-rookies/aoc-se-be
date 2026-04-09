package com.aoc.history

import com.aoc.common.ApiResponse
import com.aoc.common.BusinessException
import com.aoc.common.ErrorCode
import jakarta.persistence.criteria.Predicate
import jakarta.servlet.http.HttpServletResponse
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.web.PageableDefault
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@RestController
@RequestMapping("/histories")
@PreAuthorize("hasRole('OPERATOR')")
class HistoryController(
    private val historyRepository: HistoryRepository
) {

    @GetMapping
    fun getHistories(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate? = null,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate? = null,
        @RequestParam action: HistoryAction? = null,
        @RequestParam targetMemberId: String? = null,
        @RequestParam actorId: String? = null,
        @PageableDefault(size = 20, sort = ["createdAt"], direction = Sort.Direction.DESC) pageable: Pageable
    ): ResponseEntity<ApiResponse<Page<HistoryResponse>>> {
        val spec = buildSpec(from, to, action, targetMemberId, actorId)
        return ResponseEntity.ok(ApiResponse.ok(historyRepository.findAll(spec, pageable).map { HistoryResponse.from(it) }))
    }

    @GetMapping("/export")
    fun exportHistories(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate? = null,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate? = null,
        @RequestParam action: HistoryAction? = null,
        @RequestParam targetMemberId: String? = null,
        @RequestParam actorId: String? = null,
        response: HttpServletResponse
    ) {
        if (from != null && to != null) {
            if (from.isAfter(to)) throw BusinessException(ErrorCode.INVALID_DATE_RANGE)
            if (ChronoUnit.DAYS.between(from, to) > 90) throw BusinessException(ErrorCode.HISTORY_EXPORT_RANGE_EXCEEDED)
        }

        val spec = buildSpec(from, to, action, targetMemberId, actorId)
        val sort = Sort.by(Sort.Direction.DESC, "createdAt")
        val histories = historyRepository.findAll(spec, sort)

        val fromLabel = from?.toString() ?: "all"
        val toLabel = to?.toString() ?: "all"
        response.contentType = "text/csv; charset=UTF-8"
        response.setHeader("Content-Disposition", "attachment; filename=\"history_${fromLabel}_${toLabel}.csv\"")

        val writer = response.writer
        writer.println("id,entityType,entityId,action,actorId,operatorId,isShadow,createdAt")
        for (h in histories) {
            writer.println(
                "${h.id},${h.entityType},${h.entityId},${h.action}," +
                "${h.actorId},${h.operatorId ?: ""},${h.isShadow},${h.createdAt}"
            )
        }
        writer.flush()
    }

    companion object {
        fun buildSpec(
            from: LocalDate?,
            to: LocalDate?,
            action: HistoryAction?,
            targetMemberId: String?,
            actorId: String?
        ): Specification<History> = Specification { root, _, cb ->
            val predicates = buildList<Predicate> {
                from?.let { add(cb.greaterThanOrEqualTo(root.get("createdAt"), it.atStartOfDay())) }
                to?.let { add(cb.lessThan(root.get("createdAt"), it.plusDays(1).atStartOfDay())) }
                action?.let { add(cb.equal(root.get<HistoryAction>("action"), it)) }
                targetMemberId?.let { add(cb.equal(root.get<String>("entityId"), it)) }
                actorId?.let { add(cb.equal(root.get<String>("actorId"), it)) }
            }
            if (predicates.isEmpty()) null else cb.and(*predicates.toTypedArray())
        }
    }
}
