"""优化服务

封装路径优化和调度优化逻辑。
"""
import logging
from typing import Dict, List, Optional
from datetime import datetime, time

from app.optimization.vrp_solver import VRPSolver, VRPNode, VRPVehicle
from app.optimization.time_window import TimeWindowOptimizer, TimeSlot
from app.optimization.genetic_algorithm import (
    GeneticScheduler, GAConfig, DeliveryTask, Vehicle
)
from app.models.store_revenue import StoreRevenueModel

logger = logging.getLogger(__name__)


class OptimizationService:
    """优化服务

    提供路径优化和配送计划优化。
    """

    def __init__(self):
        self.vrp_solver = VRPSolver()
        self.time_window_optimizer = TimeWindowOptimizer()
        self.genetic_scheduler = GeneticScheduler()
        self.revenue_model = StoreRevenueModel()

    async def optimize_route(
        self,
        warehouse: Dict,
        stores: List[Dict],
        vehicles: List[Dict],
        constraints: Optional[Dict] = None,
    ) -> Dict:
        """路径优化

        Args:
            warehouse: 仓库信息 {"lat", "lon", "name"}
            stores: 门店列表
            vehicles: 车辆列表
            constraints: 额外约束

        Returns:
            优化后的路径方案
        """
        constraints = constraints or {}

        # 构建节点
        nodes = [
            VRPNode(
                node_id="warehouse",
                name=warehouse.get("name", "仓库"),
                latitude=warehouse["lat"],
                longitude=warehouse["lon"],
                demand=0,
                time_window_start=0,
                time_window_end=constraints.get("max_route_time_min", 600),
                service_time=0,
            )
        ]

        for store in stores:
            nodes.append(VRPNode(
                node_id=store["store_id"],
                name=store.get("store_name", store["store_id"]),
                latitude=store["latitude"],
                longitude=store["longitude"],
                demand=store.get("demand", 10),
                time_window_start=store.get("time_window_start_min", 0),
                time_window_end=store.get("time_window_end_min", 600),
                service_time=store.get("service_time_min", 15),
                revenue_weight=store.get("revenue_weight", 1.0),
            ))

        # 构建车辆
        vrp_vehicles = []
        for v in vehicles:
            vrp_vehicles.append(VRPVehicle(
                vehicle_id=v["vehicle_id"],
                capacity=v.get("capacity", 100),
                max_route_time=v.get("max_route_time_min", 600),
                cost_per_km=v.get("cost_per_km", 2.0),
                fixed_cost=v.get("fixed_cost", 100.0),
            ))

        # 求解
        solution = self.vrp_solver.solve(
            nodes=nodes,
            vehicles=vrp_vehicles,
            depot_index=0,
            max_solve_time_seconds=constraints.get("max_solve_time_seconds", 30),
        )

        return {
            "routes": solution.routes,
            "summary": {
                "total_distance_km": solution.total_distance,
                "total_cost": solution.total_cost,
                "total_time_min": solution.total_time,
                "vehicles_used": len(solution.routes),
                "stores_served": sum(len(r["stops"]) for r in solution.routes),
                "stores_unserved": len(solution.unserved_nodes),
            },
            "unserved_stores": solution.unserved_nodes,
            "optimization_time": datetime.now().isoformat(),
        }

    async def optimize_schedule(
        self,
        tasks: List[Dict],
        vehicles: List[Dict],
        config: Optional[Dict] = None,
    ) -> Dict:
        """配送计划优化（收益最大化）

        使用遗传算法进行多目标优化。

        Args:
            tasks: 配送任务列表
            vehicles: 车辆列表
            config: GA 配置

        Returns:
            优化后的调度方案
        """
        ga_config = GAConfig()
        if config:
            ga_config.population_size = config.get("population_size", 100)
            ga_config.generations = config.get("generations", 200)
            ga_config.cost_weight = config.get("cost_weight", 0.4)
            ga_config.revenue_weight = config.get("revenue_weight", 0.4)
            ga_config.time_weight = config.get("time_weight", 0.2)

        # 构建任务
        delivery_tasks = []
        for t in tasks:
            delivery_tasks.append(DeliveryTask(
                task_id=t["task_id"],
                store_id=t["store_id"],
                demand=t.get("demand", 10),
                optimal_arrival_hour=t.get("optimal_arrival_hour", 10),
                time_window_start=t.get("time_window_start", 6),
                time_window_end=t.get("time_window_end", 22),
                revenue_value=t.get("revenue_value", 1000.0),
                latitude=t["latitude"],
                longitude=t["longitude"],
            ))

        # 构建车辆
        ga_vehicles = []
        for v in vehicles:
            ga_vehicles.append(Vehicle(
                vehicle_id=v["vehicle_id"],
                capacity=v.get("capacity", 100),
                max_hours=v.get("max_hours", 10.0),
                cost_per_km=v.get("cost_per_km", 2.0),
                start_lat=v.get("start_lat", 39.9),
                start_lon=v.get("start_lon", 116.4),
            ))

        # 执行遗传算法
        scheduler = GeneticScheduler(ga_config)
        result = scheduler.solve(delivery_tasks, ga_vehicles)

        result["optimization_time"] = datetime.now().isoformat()
        result["config"] = {
            "population_size": ga_config.population_size,
            "generations": ga_config.generations,
            "weights": {
                "cost": ga_config.cost_weight,
                "revenue": ga_config.revenue_weight,
                "time": ga_config.time_weight,
            },
        }

        return result
