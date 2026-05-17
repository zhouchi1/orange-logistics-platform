"""遗传算法求解多约束调度问题

多目标优化：配送成本 vs 门店收益
染色体编码：配送顺序 + 到达时间
适应度函数：综合收益评分
"""
import numpy as np
from typing import Dict, List, Tuple, Optional, Callable
from dataclasses import dataclass
import random
import logging

logger = logging.getLogger(__name__)


@dataclass
class GAConfig:
    """遗传算法配置"""
    population_size: int = 100
    generations: int = 200
    crossover_rate: float = 0.8
    mutation_rate: float = 0.15
    elite_ratio: float = 0.1
    tournament_size: int = 5
    # 多目标权重
    cost_weight: float = 0.4
    revenue_weight: float = 0.4
    time_weight: float = 0.2


@dataclass
class DeliveryTask:
    """配送任务"""
    task_id: str
    store_id: str
    demand: int
    optimal_arrival_hour: int
    time_window_start: int
    time_window_end: int
    revenue_value: float
    latitude: float
    longitude: float


@dataclass
class Vehicle:
    """车辆资源"""
    vehicle_id: str
    capacity: int
    max_hours: float
    cost_per_km: float
    start_lat: float
    start_lon: float


class Chromosome:
    """染色体：编码一个完整的配送方案

    编码方式：
    - genes: 任务分配序列 [task_idx, vehicle_idx, planned_hour]
    - 每个基因代表一个任务的分配决策
    """

    def __init__(self, tasks: List[DeliveryTask], vehicles: List[Vehicle]):
        self.n_tasks = len(tasks)
        self.n_vehicles = len(vehicles)
        # 基因：每个任务分配给哪辆车
        self.vehicle_assignment = np.zeros(self.n_tasks, dtype=int)
        # 基因：每个任务的计划到达时间
        self.planned_hours = np.zeros(self.n_tasks, dtype=float)
        # 基因：每辆车内的任务顺序
        self.task_order = np.arange(self.n_tasks)
        # 适应度
        self.fitness = 0.0
        self.cost = 0.0
        self.revenue = 0.0
        self.time_penalty = 0.0

    def randomize(self, tasks: List[DeliveryTask], vehicles: List[Vehicle]):
        """随机初始化"""
        for i in range(self.n_tasks):
            self.vehicle_assignment[i] = random.randint(0, self.n_vehicles - 1)
            tw_start = tasks[i].time_window_start
            tw_end = tasks[i].time_window_end
            self.planned_hours[i] = random.uniform(tw_start, tw_end)

        np.random.shuffle(self.task_order)

    def copy(self) -> "Chromosome":
        """深拷贝"""
        new = Chromosome.__new__(Chromosome)
        new.n_tasks = self.n_tasks
        new.n_vehicles = self.n_vehicles
        new.vehicle_assignment = self.vehicle_assignment.copy()
        new.planned_hours = self.planned_hours.copy()
        new.task_order = self.task_order.copy()
        new.fitness = self.fitness
        new.cost = self.cost
        new.revenue = self.revenue
        new.time_penalty = self.time_penalty
        return new


class GeneticScheduler:
    """遗传算法配送调度优化器

    多目标优化：
    1. 最小化配送总成本（距离 + 固定成本）
    2. 最大化门店收益（按时到达的收益）
    3. 最小化时间违约（超出时间窗口的惩罚）
    """

    def __init__(self, config: Optional[GAConfig] = None):
        self.config = config or GAConfig()
        self.best_solution: Optional[Chromosome] = None
        self.history: List[Dict] = []

    def solve(
        self,
        tasks: List[DeliveryTask],
        vehicles: List[Vehicle],
        distance_func: Optional[Callable] = None,
    ) -> Dict:
        """执行遗传算法求解

        Args:
            tasks: 配送任务列表
            vehicles: 车辆列表
            distance_func: 距离计算函数

        Returns:
            最优调度方案
        """
        if distance_func is None:
            distance_func = self._haversine_distance

        # 初始化种群
        population = self._initialize_population(tasks, vehicles)

        # 评估初始种群
        for chrom in population:
            self._evaluate(chrom, tasks, vehicles, distance_func)

        # 进化循环
        for gen in range(self.config.generations):
            # 选择
            parents = self._tournament_selection(population)

            # 交叉
            offspring = self._crossover(parents, tasks, vehicles)

            # 变异
            self._mutate(offspring, tasks, vehicles)

            # 评估后代
            for chrom in offspring:
                self._evaluate(chrom, tasks, vehicles, distance_func)

            # 精英保留 + 替换
            population = self._survivor_selection(population, offspring)

            # 记录历史
            best = max(population, key=lambda c: c.fitness)
            avg_fitness = np.mean([c.fitness for c in population])

            if gen % 20 == 0:
                logger.info(
                    f"Gen {gen}: best_fitness={best.fitness:.4f}, "
                    f"avg={avg_fitness:.4f}, cost={best.cost:.2f}, "
                    f"revenue={best.revenue:.2f}"
                )

            self.history.append({
                "generation": gen,
                "best_fitness": best.fitness,
                "avg_fitness": avg_fitness,
                "best_cost": best.cost,
                "best_revenue": best.revenue,
            })

        # 返回最优解
        self.best_solution = max(population, key=lambda c: c.fitness)
        return self._decode_solution(self.best_solution, tasks, vehicles)

    def _initialize_population(
        self, tasks: List[DeliveryTask], vehicles: List[Vehicle]
    ) -> List[Chromosome]:
        """初始化种群"""
        population = []
        for _ in range(self.config.population_size):
            chrom = Chromosome(tasks, vehicles)
            chrom.randomize(tasks, vehicles)
            population.append(chrom)
        return population

    def _evaluate(
        self,
        chrom: Chromosome,
        tasks: List[DeliveryTask],
        vehicles: List[Vehicle],
        distance_func: Callable,
    ):
        """评估适应度"""
        total_cost = 0.0
        total_revenue = 0.0
        total_time_penalty = 0.0

        # 按车辆分组
        vehicle_tasks: Dict[int, List[int]] = {v: [] for v in range(len(vehicles))}
        for task_idx in chrom.task_order:
            v_idx = chrom.vehicle_assignment[task_idx]
            vehicle_tasks[v_idx].append(task_idx)

        for v_idx, task_indices in vehicle_tasks.items():
            if not task_indices:
                continue

            vehicle = vehicles[v_idx]
            route_distance = 0.0
            current_lat = vehicle.start_lat
            current_lon = vehicle.start_lon
            current_time = 6.0  # 早上6点出发
            total_demand = 0

            for task_idx in task_indices:
                task = tasks[task_idx]
                total_demand += task.demand

                # 容量检查
                if total_demand > vehicle.capacity:
                    total_cost += 10000  # 超容量惩罚
                    break

                # 计算距离和时间
                dist = distance_func(
                    current_lat, current_lon, task.latitude, task.longitude
                )
                route_distance += dist
                travel_time = dist / 30.0  # 假设30km/h
                current_time += travel_time + 0.25  # 加15分钟服务时间

                # 时间窗口检查
                planned_hour = chrom.planned_hours[task_idx]
                if current_time < task.time_window_start:
                    current_time = task.time_window_start  # 等待
                elif current_time > task.time_window_end:
                    # 超出时间窗口惩罚
                    overtime = current_time - task.time_window_end
                    total_time_penalty += overtime * 100

                # 收益计算：越接近最优到达时间，收益越高
                time_diff = abs(current_time - task.optimal_arrival_hour)
                revenue_factor = max(0, 1.0 - time_diff / 4.0)  # 4小时内线性衰减
                total_revenue += task.revenue_value * revenue_factor

                current_lat = task.latitude
                current_lon = task.longitude

            # 工时检查
            if current_time - 6.0 > vehicle.max_hours:
                total_time_penalty += (current_time - 6.0 - vehicle.max_hours) * 200

            total_cost += route_distance * vehicle.cost_per_km

        # 综合适应度（越高越好）
        normalized_cost = total_cost / (len(tasks) * 10 + 1)
        normalized_revenue = total_revenue / (sum(t.revenue_value for t in tasks) + 1)
        normalized_penalty = total_time_penalty / (len(tasks) * 100 + 1)

        chrom.fitness = (
            self.config.revenue_weight * normalized_revenue
            - self.config.cost_weight * normalized_cost
            - self.config.time_weight * normalized_penalty
        )
        chrom.cost = total_cost
        chrom.revenue = total_revenue
        chrom.time_penalty = total_time_penalty

    def _tournament_selection(self, population: List[Chromosome]) -> List[Chromosome]:
        """锦标赛选择"""
        selected = []
        for _ in range(len(population)):
            tournament = random.sample(population, min(self.config.tournament_size, len(population)))
            winner = max(tournament, key=lambda c: c.fitness)
            selected.append(winner.copy())
        return selected

    def _crossover(
        self,
        parents: List[Chromosome],
        tasks: List[DeliveryTask],
        vehicles: List[Vehicle],
    ) -> List[Chromosome]:
        """交叉操作"""
        offspring = []
        for i in range(0, len(parents) - 1, 2):
            if random.random() < self.config.crossover_rate:
                child1, child2 = self._order_crossover(parents[i], parents[i + 1])
            else:
                child1, child2 = parents[i].copy(), parents[i + 1].copy()
            offspring.extend([child1, child2])
        return offspring

    def _order_crossover(
        self, parent1: Chromosome, parent2: Chromosome
    ) -> Tuple[Chromosome, Chromosome]:
        """顺序交叉（OX）"""
        child1 = parent1.copy()
        child2 = parent2.copy()
        n = parent1.n_tasks

        # 车辆分配：均匀交叉
        mask = np.random.random(n) < 0.5
        child1.vehicle_assignment[mask] = parent2.vehicle_assignment[mask]
        child2.vehicle_assignment[~mask] = parent1.vehicle_assignment[~mask]

        # 计划时间：算术交叉
        alpha = np.random.random(n)
        child1.planned_hours = alpha * parent1.planned_hours + (1 - alpha) * parent2.planned_hours
        child2.planned_hours = (1 - alpha) * parent1.planned_hours + alpha * parent2.planned_hours

        # 任务顺序：OX 交叉
        start, end = sorted(random.sample(range(n), 2))
        child1.task_order = self._ox_order(parent1.task_order, parent2.task_order, start, end)
        child2.task_order = self._ox_order(parent2.task_order, parent1.task_order, start, end)

        return child1, child2

    def _ox_order(
        self, p1: np.ndarray, p2: np.ndarray, start: int, end: int
    ) -> np.ndarray:
        """OX 顺序交叉辅助"""
        n = len(p1)
        child = np.full(n, -1, dtype=int)
        child[start:end] = p1[start:end]

        remaining = [g for g in p2 if g not in child[start:end]]
        idx = 0
        for i in range(n):
            if child[i] == -1:
                child[i] = remaining[idx]
                idx += 1
        return child

    def _mutate(
        self,
        offspring: List[Chromosome],
        tasks: List[DeliveryTask],
        vehicles: List[Vehicle],
    ):
        """变异操作"""
        for chrom in offspring:
            if random.random() < self.config.mutation_rate:
                mutation_type = random.choice(["swap", "reassign", "time_shift"])

                if mutation_type == "swap":
                    # 交换两个任务的顺序
                    i, j = random.sample(range(chrom.n_tasks), 2)
                    chrom.task_order[i], chrom.task_order[j] = (
                        chrom.task_order[j], chrom.task_order[i]
                    )
                elif mutation_type == "reassign":
                    # 重新分配一个任务到另一辆车
                    i = random.randint(0, chrom.n_tasks - 1)
                    chrom.vehicle_assignment[i] = random.randint(0, chrom.n_vehicles - 1)
                elif mutation_type == "time_shift":
                    # 调整计划到达时间
                    i = random.randint(0, chrom.n_tasks - 1)
                    task = tasks[i]
                    shift = random.gauss(0, 1)
                    new_time = chrom.planned_hours[i] + shift
                    chrom.planned_hours[i] = np.clip(
                        new_time, task.time_window_start, task.time_window_end
                    )

    def _survivor_selection(
        self, population: List[Chromosome], offspring: List[Chromosome]
    ) -> List[Chromosome]:
        """精英保留 + 后代替换"""
        combined = population + offspring
        combined.sort(key=lambda c: c.fitness, reverse=True)

        elite_count = int(self.config.population_size * self.config.elite_ratio)
        new_pop = combined[:elite_count]

        # 剩余从后代中选择
        remaining = [c for c in offspring if c not in new_pop]
        remaining.sort(key=lambda c: c.fitness, reverse=True)
        new_pop.extend(remaining[: self.config.population_size - elite_count])

        # 如果不够，从原种群补充
        if len(new_pop) < self.config.population_size:
            new_pop.extend(combined[len(new_pop): self.config.population_size])

        return new_pop[: self.config.population_size]

    def _decode_solution(
        self,
        chrom: Chromosome,
        tasks: List[DeliveryTask],
        vehicles: List[Vehicle],
    ) -> Dict:
        """解码染色体为可读方案"""
        routes = {}
        for v_idx in range(len(vehicles)):
            routes[vehicles[v_idx].vehicle_id] = []

        for task_idx in chrom.task_order:
            v_idx = chrom.vehicle_assignment[task_idx]
            task = tasks[task_idx]
            routes[vehicles[v_idx].vehicle_id].append({
                "task_id": task.task_id,
                "store_id": task.store_id,
                "planned_arrival_hour": round(float(chrom.planned_hours[task_idx]), 2),
                "optimal_arrival_hour": task.optimal_arrival_hour,
                "revenue_value": task.revenue_value,
            })

        # 过滤空路线
        active_routes = {k: v for k, v in routes.items() if v}

        return {
            "routes": active_routes,
            "total_cost": round(chrom.cost, 2),
            "total_revenue": round(chrom.revenue, 2),
            "time_penalty": round(chrom.time_penalty, 2),
            "fitness": round(chrom.fitness, 6),
            "generations_run": len(self.history),
            "convergence_history": self.history[-10:] if self.history else [],
        }

    @staticmethod
    def _haversine_distance(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
        """Haversine 距离（km）"""
        R = 6371.0
        lat1_r, lat2_r = np.radians(lat1), np.radians(lat2)
        dlat = np.radians(lat2 - lat1)
        dlon = np.radians(lon2 - lon1)
        a = np.sin(dlat / 2) ** 2 + np.cos(lat1_r) * np.cos(lat2_r) * np.sin(dlon / 2) ** 2
        return R * 2 * np.arctan2(np.sqrt(a), np.sqrt(1 - a))
