import random

GRID_SIZE = 20

class BattleshipsAI:
    def __init__(self):
        self.player_grid = [[0 for _ in range(GRID_SIZE)] for _ in range(GRID_SIZE)] # 0: unknown, 1: miss, 2: hit, 3: sunk
        self.sunken_ships = [] # List of lists of coordinates
        self.hits = [] # Current hits that haven't resulted in a sunk ship yet
        self.remaining_ship_defs = [] # List of relative coordinate lists
        self.all_rotations_cache = {} # Cache for rotated ship definitions
        
    def place_ships(self, ship_definitions):
        """
        ship_definitions: list of relative coordinate lists
        Returns: list of absolute coordinate lists for each ship
        """
        self.remaining_ship_defs = [list(d) for d in ship_definitions]
        self._precompute_rotations()
        
        placed_ships = []
        for ship_coords in ship_definitions:
            placed = False
            while not placed:
                # Random rotation
                rotations = random.randint(0, 3)
                current_coords = ship_coords
                for _ in range(rotations):
                    current_coords = self._rotate(current_coords)
                
                # Random position
                start_x = random.randint(0, GRID_SIZE - 1)
                start_y = random.randint(0, GRID_SIZE - 1)
                
                abs_coords = [(start_x + x, start_y + y) for x, y in current_coords]
                
                if self._is_legal_placement(abs_coords, placed_ships):
                    placed_ships.append(abs_coords)
                    placed = True
                    
        return placed_ships

    def _precompute_rotations(self):
        self.all_rotations_cache = {}
        for i, ship_def in enumerate(self.remaining_ship_defs):
            rotations = []
            current = ship_def
            for _ in range(4):
                # Store as set of tuples for fast lookup
                rotations.append(set(tuple(p) for p in current))
                current = self._rotate(current)
            self.all_rotations_cache[i] = rotations

    def _rotate(self, coords):
        rotated = [(-y, x) for x, y in coords]
        min_x = min(x for x, y in rotated)
        min_y = min(y for x, y in rotated)
        return [(x - min_x, y - min_y) for x, y in rotated]

    def _is_legal_placement(self, coords, existing_ships):
        for x, y in coords:
            if x < 0 or x >= GRID_SIZE or y < 0 or y >= GRID_SIZE:
                return False
            for other_ship in existing_ships:
                for ox, oy in other_ship:
                    if abs(x - ox) <= 1 and abs(y - oy) <= 1:
                        return False
        return True

    def make_shot(self):
        """
        Returns (x, y) coordinate for the next shot.
        """
        # 1. If there are active hits, target nearby blocks
        if self.hits:
            target = self._get_smart_target()
            if target:
                return target
        
        # 2. Otherwise, pick a random legal spot that could actually contain a ship
        # We use a parity-like check to ensure the spot is part of a valid potential placement
        legal_spots = []
        for y in range(GRID_SIZE):
            for x in range(GRID_SIZE):
                if self._is_shot_legal(x, y) and self._could_ship_fit_at(x, y):
                    legal_spots.append((x, y))
        
        if legal_spots:
            return random.choice(legal_spots)
            
        # Fallback to any legal spot if the "fit" check is too restrictive (shouldn't happen)
        while True:
            x = random.randint(0, GRID_SIZE - 1)
            y = random.randint(0, GRID_SIZE - 1)
            if self._is_shot_legal(x, y):
                return (x, y)

    def _is_shot_legal(self, x, y):
        if x < 0 or x >= GRID_SIZE or y < 0 or y >= GRID_SIZE:
            return False
        if self.player_grid[y][x] != 0:
            return False
        
        # AI must not shoot at a block where it would be illegal to have a ship
        # (i.e. adjacent to a sunken ship)
        for ship in self.sunken_ships:
            for sx, sy in ship:
                if abs(x - sx) <= 1 and abs(y - sy) <= 1:
                    return False
        return True

    def _could_ship_fit_at(self, x, y, hits_to_include=None):
        """
        Checks if any remaining ship can be placed such that it covers (x, y)
        and all coordinates in hits_to_include, without hitting misses or sunken ships.
        """
        if hits_to_include is None:
            hits_to_include = []
            
        for ship_idx in range(len(self.remaining_ship_defs)):
            rotations = self.all_rotations_cache[ship_idx]
            for rotated_def in rotations:
                # Try all possible offsets such that the ship covers (x, y)
                for dx, dy in rotated_def:
                    start_x, start_y = x - dx, y - dy
                    abs_coords = [(start_x + sx, start_y + sy) for sx, sy in rotated_def]
                    
                    if self._is_potential_placement_valid(abs_coords, hits_to_include):
                        return True
        return False

    def _is_potential_placement_valid(self, coords, hits_to_include):
        coord_set = set(coords)
        
        # Must include all hits we are currently targeting
        for h in hits_to_include:
            if h not in coord_set:
                return False
        
        for cx, cy in coords:
            # Within grid
            if cx < 0 or cx >= GRID_SIZE or cy < 0 or cy >= GRID_SIZE:
                return False
            
            # Not a miss or sunken ship
            if self.player_grid[cy][cx] == 1 or self.player_grid[cy][cx] == 3:
                return False
            
            # Adjacency to sunken ships
            for ship in self.sunken_ships:
                for sx, sy in ship:
                    if abs(cx - sx) <= 1 and abs(cy - sy) <= 1:
                        return False
            
            # Adjacency to OTHER hits (hits not in this potential ship)
            # Rule: if we touch a hit, it MUST be part of this ship.
            for hx, hy in self.hits:
                if abs(cx - hx) <= 1 and abs(cy - hy) <= 1:
                    if (hx, hy) not in coord_set:
                        return False
        return True

    def _get_smart_target(self):
        if not self.hits:
            return None
            
        # Try to find a line in the current hits
        if len(self.hits) >= 2:
            for i in range(len(self.hits)):
                for j in range(i + 1, len(self.hits)):
                    h1 = self.hits[i]
                    h2 = self.hits[j]
                    
                    dx = h2[0] - h1[0]
                    dy = h2[1] - h1[1]
                    
                    if (dx == 0 and abs(dy) >= 1) or (dy == 0 and abs(dx) >= 1):
                        ndx = 0 if dx == 0 else (1 if dx > 0 else -1)
                        ndy = 0 if dy == 0 else (1 if dy > 0 else -1)
                        
                        # Try to extend the line at both ends
                        for direction in [1, -1]:
                            curr_x, curr_y = h1[0], h1[1]
                            line_hits = [h1]
                            while (curr_x + ndx * direction, curr_y + ndy * direction) in self.hits:
                                curr_x += ndx * direction
                                curr_y += ndy * direction
                                line_hits.append((curr_x, curr_y))
                            
                            tx, ty = curr_x + ndx * direction, curr_y + ndy * direction
                            if self._is_shot_legal(tx, ty) and self._could_ship_fit_at(tx, ty, line_hits):
                                return (tx, ty)

        # Fallback: try neighbors of all hits
        shuffled_hits = list(self.hits)
        random.shuffle(shuffled_hits)
        for hx, hy in shuffled_hits:
            neighbors = [(hx, hy + 1), (hx, hy - 1), (hx + 1, hy), (hx - 1, hy)]
            random.shuffle(neighbors)
            for tx, ty in neighbors:
                if self._is_shot_legal(tx, ty) and self._could_ship_fit_at(tx, ty, [(hx, hy)]):
                    return (tx, ty)
        return None

    def update_result(self, x, y, is_hit, is_sunk, ship_coords=None):
        if is_hit:
            self.player_grid[y][x] = 2
            self.hits.append((x, y))
            if is_sunk:
                # Convert ship_coords to list of tuples for consistency
                s_coords = [tuple(p) for p in ship_coords]
                for sx, sy in s_coords:
                    self.player_grid[sy][sx] = 3
                    if (sx, sy) in self.hits:
                        self.hits.remove((sx, sy))
                self.sunken_ships.append(s_coords)
                self._remove_remaining_ship(s_coords)
        else:
            self.player_grid[y][x] = 1

    def _remove_remaining_ship(self, sunken_coords):
        """
        Identifies which ship definition matches the sunken ship and removes it.
        """
        # Normalize sunken coords
        min_x = min(x for x, y in sunken_coords)
        min_y = min(y for x, y in sunken_coords)
        norm_sunken = set((x - min_x, y - min_y) for x, y in sunken_coords)
        
        for i in range(len(self.remaining_ship_defs)):
            rotations = self.all_rotations_cache[i]
            match = False
            for rotated_def in rotations:
                if rotated_def == norm_sunken:
                    match = True
                    break
            if match:
                self.remaining_ship_defs.pop(i)
                # Re-index cache
                self._precompute_rotations()
                return

# Instance for GraalVM
ai = BattleshipsAI()
