"""OR-Tools 车辆路径问题求解器

实现 VRPTW（带时间窗的车辆路径问题）。
约束：车辆容量、时间窗口、司机工时。
目标：最小化总成本 + 最大化门店收益。
"""
from ortools.constraint_solver import routing_enums_pb2, pywrapcp
from typing import Dict, List, Optional, Tuple
from dataclasses import dataclass
import numpy as np
import logging

logger = logging.getLogger(__name__)


@dataclass
class VRPNode:
    """路径节点"""
    node_id: str
    name: str
    latitude: float
    longitude: float
    demand: int  # 需求量（件）
    time_window_start: int  # 时间窗口开始（分钟，从0点算）
    time_window_end: int  # 时间窗口结束（分钟）
    service_time: int  # 服务时间（分钟）
    revenue_weight: float = 1.0  # 收益权重


@dataclass
class VRPVehicle:
    """车辆"""
    vehicle_id: str
    capacity: int  # 容量（件）
    max_route_time: int  # 最大行驶时间（分钟）
    cost_per_km: float  # 每公里成本
    fixed_cost: float  # 固定成本


@dataclass
class VRPSolution:
    """求解结果"""
    routes: List[Dict]
    total_distance: float
    total_cost: float
    total_time: int
    unserved_nodes: List[str]
    objective_value: float


class VRPSolver:
    """VRPTW 求解器

    使用 Google OR-Tools 求解带时间窗的车辆路径问题。
    支持多种约束和目标函数。
    """

    def __init__(self):
        self.distance_matrix: Optional[np.ndarray] = None
        self.time_matrix: Optional[np.ndarray] = None

    def compute_distance_matrix(self, nodes: List[VRPNode]) -> np.ndarray:
        """计算距离矩阵（使用 Haversine 公式）"""
        n = len(nodes)
        matrix = np.zeros((n, n))

        for i in range(n):
            for j in range(n):
                if i != j:
                    matrix[i][j] = self._haversine(
                        nodes[i].latitude, nodes[i].longitude,
                        nodes[j].latitude, nodes[j].longitude,
                    )
        return matrix

    def compute_time_matrix(
        self, distance_matrix: np.ndarray, avg_speed_kmh: float = 30.0
    ) -> np.ndarray:
        """计算时间矩阵（分钟）"""
        return (distance_matrix / avg_speed_kmh) * 60

    def solve(
        self,
        nodes: List[VRPNode],
        vehicles: List[VRPVehicle],
        depot_index: int = 0,
        distance_matrix: Optional[np.ndarray] = None,
        time_matrix: Optional[np.ndarray] = None,
        avg_speed_kmh: float = 30.0,
        max_solve_time_seconds: int = 30,
        revenue_weights: Optional[List[float]] = None,
    ) -> VRPSolution:
        """求解 VRPTW

        Args:
            nodes: 所有节点（包含仓库）
            vehicles: 车辆列表
            depot_index: 仓库节点索引
            distance_matrix: 距离矩阵（km），None 则自动计算
            time_matrix: 时间矩阵（分钟），None 则自动计算
            avg_speed_kmh: 平均速度
            max_solve_time_seconds: 最大求解时间
            revenue_weights: 节点收益权重

        Returns:
            VRPSolution 求解结果
        """
        num_nodes = len(nodes)
        num_vehicles = len(vehicles)

        if distance_matrix is None:
            distance_matrix = self.compute_distance_matrix(nodes)
        if time_matrix is None:
            time_matrix = self.compute_time_matrix(distance_matrix, avg_speed_kmh)

        self.distance_matrix = distance_matrix
        self.time_matrix = time_matrix

        # 创建路由模型
        manager = pywrapcp.RoutingIndexManager(num_nodes, num_vehicles, depot_index)
        routing = pywrapcp.RoutingModel(manager)

        # 距离回调
        def distance_callback(from_index, to_index):
            from_node = manager.IndexToNode(from_index)
            to_node = manager.IndexToNode(to_index)
            return int(distance_matrix[from_node][to_node] * 1000)  # 转为米

        transit_callback_index = routing.RegisterTransitCallback(distance_callback)
        routing.SetArcCostEvaluatorOfAllVehicles(transit_callback_index)

        # 时间维度
        def time_callback(from_index, to_index):
            from_node = manager.IndexToNode(from_index)
            to_node = manager.IndexToNode(to_index)
            travel_time = int(time_matrix[from_node][to_node])
            service_time = nodes[from_node].service_time
            return travel_time + service_time

        time_callback_index = routing.RegisterTransitCallback(time_callback)

        max_time = max(v.max_route_time for v in vehicles)
        routing.AddDimension(
            time_callback_index,
            60,  # 允许等待时间（分钟）
            max_time,
            False,
            "Time",
        )
        time_dimension = routing.GetDimensionOrDie("Time")

        # 时间窗口约束
        for node_idx in range(num_nodes):
            if node_idx == depot_index:
                continue
            index = manager.NodeToIndex(node_idx)
            time_dimension.CumulVar(index).SetRange(
                nodes[node_idx].time_window_start,
                nodes[node_idx].time_window_end,
            )

        # 仓库时间窗口
        for v in range(num_vehicles):
            start_index = routing.Start(v)
            time_dimension.CumulVar(start_index).SetRange(0, max_time)
            end_index = routing.End(v)
            time_dimension.CumulVar(end_index).SetRange(0, max_time)

        # 容量约束
        def demand_callback(from_index):
            from_node = manager.IndexToNode(from_index)
            return nodes[from_node].demand

        demand_callback_index = routing.RegisterUnaryTransitCallback(demand_callback)
        vehicle_capacities = [v.capacity for v in vehicles]
        routing.AddDimensionWithVehicleCapacity(
            demand_callback_index,
            0,
            vehicle_capacities,
            True,
            "Capacity",
        )

        # 允许丢弃节点（带惩罚）
        penalty = 100000
        for node_idx in range(num_nodes):
            if node_idx == depot_index:
                continue
            routing.AddDisjunction([manager.NodeToIndex(node_idx)], penalty)

        # 求解参数
        search_parameters = pywrapcp.DefaultRoutingSearchParameters()
        search_parameters.first_solution_strategy = (
            routing_enums_pb2.FirstSolutionStrategy.PATH_CHEAPEST_ARC
        )
        search_parameters.local_search_metaheuristic = (
            routing_enums_pb2.LocalSearchMetaheuristic.GUIDED_LOCAL_SEARCH
        )
        search_parameters.time_limit.seconds = max_solve_time_seconds

        # 求解
        solution = routing.SolveWithParameters(search_parameters)

        if solution:
            return self._extract_solution(
                manager, routing, solution, nodes, vehicles, distance_matrix, time_dimension
            )
        else:
            logger.warning("No solution found for VRP")
            return VRPSolution(
                routes=[],
                total_distance=0.0,
                total_cost=0.0,
                total_time=0,
                unserved_nodes=[n.node_id for n in nodes if n != nodes[depot_index]],
                objective_value=float("inf"),
            )

    def _extract_solution(
        self,
        manager: pywrapcp.RoutingIndexManager,
        routing: pywrapcp.RoutingModel,
        solution,
        nodes: List[VRPNode],
        vehicles: List[VRPVehicle],
        distance_matrix: np.ndarray,
        time_dimension,
    ) -> VRPSolution:
        """提取求解结果"""
        routes = []
        total_distance = 0.0
        total_cost = 0.0
        total_time = 0
        served_nodes = set()

        for vehicle_idx in range(len(vehicles)):
            route_nodes = []
            route_distance = 0.0
            index = routing.Start(vehicle_idx)

            while not routing.IsEnd(index):
                node_idx = manager.IndexToNode(index)
                time_var = time_dimension.CumulVar(index)
                arrival_time = solution.Min(time_var)

                route_nodes.append({
                    "node_id": nodes[node_idx].node_id,
                    "name": nodes[node_idx].name,
                    "arrival_time_min": arrival_time,
                    "demand": nodes[node_idx].demand,
                })
                served_nodes.add(node_idx)

                prev_index = index
                index = solution.Value(routing.NextVar(index))
                from_node = manager.IndexToNode(prev_index)
                to_node = manager.IndexToNode(index) if not routing.IsEnd(index) else 0
                route_distance += distance_matrix[from_node][to_node]

            if len(route_nodes) > 1:  # 不只是仓库
                route_cost = (
                    vehicles[vehicle_idx].fixed_cost
                    + route_distance * vehicles[vehicle_idx].cost_per_km
                )
                route_time = solution.Min(time_dimension.CumulVar(routing.End(vehicle_idx)))

                routes.append({
                    "vehicle_id": vehicles[vehicle_idx].vehicle_id,
                    "stops": route_nodes[1:],  # 排除仓库
                    "distance_km": round(route_distance, 2),
                    "cost": round(route_cost, 2),
                    "duration_min": route_time,
                })

                total_distance += route_distance
                total_cost += route_cost
                total_time = max(total_time, route_time)

        # 未服务节点
        unserved = [
            nodes[i].node_id
            for i in range(len(nodes))
            if i not in served_nodes and i != 0
        ]

        return VRPSolution(
            routes=routes,
            total_distance=round(total_distance, 2),
            total_cost=round(total_cost, 2),
            total_time=total_time,
            unserved_nodes=unserved,
            objective_value=solution.ObjectiveValue(),
        )

    @staticmethod
    def _haversine(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
        """Haversine 公式计算两点间距离（km）"""
        R = 6371.0
        lat1_r, lat2_r = np.radians(lat1), np.radians(lat2)
        dlat = np.radians(lat2 - lat1)
        dlon = np.radians(lon2 - lon1)

        a = (
            np.sin(dlat / 2) ** 2
            + np.cos(lat1_r) * np.cos(lat2_r) * np.sin(dlon / 2) ** 2
        )
        c = 2 * np.arctan2(np.sqrt(a), np.sqrt(1 - a))
        return R * c
