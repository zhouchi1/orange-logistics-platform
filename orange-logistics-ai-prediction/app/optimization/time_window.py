"""时间窗口优化

基于门店收益模型和配送约束，优化配送时间窗口分配。
"""
from typing import Dict, List, Tuple, Optional
from dataclasses import dataclass
import numpy as np
from datetime import time
import logging

from app.models.store_revenue import StoreRevenueModel, StoreProfile

logger = logging.getLogger(__name__)


@dataclass
class TimeSlot:
    """时间槽"""
    start_hour: int
    end_hour: int
    capacity: int  # 该时间段可服务的门店数


@dataclass
class TimeWindowAssignment:
    """时间窗口分配结果"""
    store_id: str
    assigned_window_start: int
    assigned_window_end: int
    priority: int
    expected_revenue: float


class TimeWindowOptimizer:
    """时间窗口优化器

    在有限的配送资源下，为每个门店分配最优的配送时间窗口。
    目标：最大化所有门店的总收益。
    """

    def __init__(self):
        self.revenue_model = StoreRevenueModel()

    def optimize_time_windows(
        self,
        stores: List[Dict],
        available_slots: List[TimeSlot],
        inventory_data: Optional[Dict[str, float]] = None,
    ) -> List[TimeWindowAssignment]:
        """优化时间窗口分配

        使用贪心 + 局部搜索策略：
        1. 计算每个门店在每个时间槽的收益
        2. 按紧急度和收益排序
        3. 贪心分配
        4. 局部搜索改进

        Args:
            stores: 门店列表
            available_slots: 可用时间槽
            inventory_data: 库存数据

        Returns:
            时间窗口分配结果
        """
        # 计算每个门店在每个时间槽的收益矩阵
        n_stores = len(stores)
        n_slots = len(available_slots)
        revenue_matrix = np.zeros((n_stores, n_slots))

        store_profiles = []
        for i, store_data in enumerate(stores):
            profile = StoreProfile(
                store_id=store_data["store_id"],
                store_name=store_data.get("store_name", ""),
                open_time=time(store_data.get("open_hour", 8), 0),
                close_time=time(store_data.get("close_hour", 22), 0),
                peak_hours=store_data.get("peak_hours", [11, 12, 17, 18]),
                avg_hourly_sales=store_data.get("avg_hourly_sales", 5000.0),
                inventory_decay_rate=store_data.get("inventory_decay_rate", 0.1),
                restock_urgency=store_data.get("restock_urgency", 0.5),
                store_type=store_data.get("store_type", "convenience"),
                daily_revenue=store_data.get("daily_revenue", 50000.0),
            )
            store_profiles.append(profile)

            inv_ratio = 0.5
            if inventory_data and store_data["store_id"] in inventory_data:
                inv_ratio = inventory_data[store_data["store_id"]]

            for j, slot in enumerate(available_slots):
                # 取时间槽中间时间计算收益
                mid_hour = (slot.start_hour + slot.end_hour) // 2
                revenue_matrix[i][j] = self.revenue_model.compute_arrival_revenue(
                    profile, mid_hour, inv_ratio
                )

        # 贪心分配
        assignments = self._greedy_assign(
            stores, store_profiles, available_slots, revenue_matrix
        )

        # 局部搜索优化
        assignments = self._local_search(
            assignments, stores, available_slots, revenue_matrix
        )

        return assignments

    def _greedy_assign(
        self,
        stores: List[Dict],
        profiles: List[StoreProfile],
        slots: List[TimeSlot],
        revenue_matrix: np.ndarray,
    ) -> List[TimeWindowAssignment]:
        """贪心分配策略"""
        n_stores = len(stores)
        n_slots = len(slots)

        # 计算优先级：紧急度 * 最大可能收益
        priorities = []
        for i in range(n_stores):
            max_rev = revenue_matrix[i].max()
            urgency = profiles[i].restock_urgency
            priorities.append((i, urgency * 0.4 + (max_rev / (max_rev + 1)) * 0.6))

        # 按优先级降序排列
        priorities.sort(key=lambda x: x[1], reverse=True)

        # 时间槽剩余容量
        slot_remaining = [s.capacity for s in slots]
        assignments = []

        for store_idx, priority_score in priorities:
            # 找到收益最高且有容量的时间槽
            best_slot = -1
            best_revenue = -1

            for j in range(n_slots):
                if slot_remaining[j] > 0 and revenue_matrix[store_idx][j] > best_revenue:
                    best_revenue = revenue_matrix[store_idx][j]
                    best_slot = j

            if best_slot >= 0:
                slot_remaining[best_slot] -= 1
                assignments.append(TimeWindowAssignment(
                    store_id=stores[store_idx]["store_id"],
                    assigned_window_start=slots[best_slot].start_hour,
                    assigned_window_end=slots[best_slot].end_hour,
                    priority=int(priority_score * 100),
                    expected_revenue=best_revenue,
                ))
            else:
                # 无可用时间槽，分配最后一个
                assignments.append(TimeWindowAssignment(
                    store_id=stores[store_idx]["store_id"],
                    assigned_window_start=slots[-1].start_hour,
                    assigned_window_end=slots[-1].end_hour,
                    priority=int(priority_score * 100),
                    expected_revenue=0.0,
                ))

        return assignments

    def _local_search(
        self,
        assignments: List[TimeWindowAssignment],
        stores: List[Dict],
        slots: List[TimeSlot],
        revenue_matrix: np.ndarray,
        max_iterations: int = 100,
    ) -> List[TimeWindowAssignment]:
        """局部搜索优化：尝试交换分配以提高总收益"""
        n = len(assignments)
        improved = True
        iteration = 0

        while improved and iteration < max_iterations:
            improved = False
            iteration += 1

            for i in range(n):
                for j in range(i + 1, n):
                    # 尝试交换 i 和 j 的时间窗口
                    current_revenue = (
                        assignments[i].expected_revenue + assignments[j].expected_revenue
                    )

                    # 找到对应的 slot 索引
                    slot_i = next(
                        (k for k, s in enumerate(slots)
                         if s.start_hour == assignments[i].assigned_window_start),
                        None,
                    )
                    slot_j = next(
                        (k for k, s in enumerate(slots)
                         if s.start_hour == assignments[j].assigned_window_start),
                        None,
                    )

                    if slot_i is None or slot_j is None or slot_i == slot_j:
                        continue

                    # 找到 store 索引
                    store_i = next(
                        (k for k, s in enumerate(stores)
                         if s["store_id"] == assignments[i].store_id),
                        None,
                    )
                    store_j = next(
                        (k for k, s in enumerate(stores)
                         if s["store_id"] == assignments[j].store_id),
                        None,
                    )

                    if store_i is None or store_j is None:
                        continue

                    # 交换后的收益
                    swapped_revenue = (
                        revenue_matrix[store_i][slot_j]
                        + revenue_matrix[store_j][slot_i]
                    )

                    if swapped_revenue > current_revenue:
                        # 执行交换
                        assignments[i].assigned_window_start = slots[slot_j].start_hour
                        assignments[i].assigned_window_end = slots[slot_j].end_hour
                        assignments[i].expected_revenue = revenue_matrix[store_i][slot_j]

                        assignments[j].assigned_window_start = slots[slot_i].start_hour
                        assignments[j].assigned_window_end = slots[slot_i].end_hour
                        assignments[j].expected_revenue = revenue_matrix[store_j][slot_i]

                        improved = True

        logger.info(f"Local search completed in {iteration} iterations")
        return assignments
