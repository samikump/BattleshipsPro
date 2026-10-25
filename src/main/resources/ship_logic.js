/**
 * ship_logic.js
 * Handles ship rotation and collision detection for BattleshipsPro.
 * Optimized for GraalVM Polyglot compatibility.
 */

const GRID_SIZE = 20;

/**
 * Helper to access x/y from either JS object or Java Map.
 */
function getX(p) { return p.x !== undefined ? p.x : (p.get ? p.get('x') : undefined); }
function getY(p) { return p.y !== undefined ? p.y : (p.get ? p.get('y') : undefined); }

/**
 * Rotates a set of coordinates 90 degrees clockwise.
 * @param {Object} coords - Java List or JS Array of {x, y} objects.
 * @returns {Array} - Rotated coordinates.
 */
function rotateShip(coords) {
    const input = Array.from(coords);
    let rotated = [];
    
    // (x, y) -> (-y, x)
    for (let i = 0; i < input.length; i++) {
        let p = input[i];
        rotated.push({ x: -getY(p), y: getX(p) });
    }
    
    // Normalize to top-left (0,0)
    let minX = getX(rotated[0]);
    let minY = getY(rotated[0]);
    for (let i = 1; i < rotated.length; i++) {
        if (getX(rotated[i]) < minX) minX = getX(rotated[i]);
        if (getY(rotated[i]) < minY) minY = getY(rotated[i]);
    }
    
    let normalized = [];
    for (let i = 0; i < rotated.length; i++) {
        normalized.push({ x: getX(rotated[i]) - minX, y: getY(rotated[i]) - minY });
    }
    
    return normalized;
}

/**
 * Checks if a ship placement is legal.
 * @param {Object} shipCoords - Java List or JS Array of absolute coordinates.
 * @param {Object} existingShips - Java List of Java Lists of absolute coordinates.
 * @returns {boolean} - True if legal, false otherwise.
 */
function isPlacementLegal(shipCoords, existingShips) {
    const sCoords = Array.from(shipCoords);
    const eShips = Array.from(existingShips);
    
    for (let i = 0; i < sCoords.length; i++) {
        let p = sCoords[i];
        let px = getX(p);
        let py = getY(p);
        
        // Check grid boundaries
        if (px < 0 || px >= GRID_SIZE || py < 0 || py >= GRID_SIZE) {
            return false;
        }
        
        // Check collision and adjacency with existing ships
        for (let j = 0; j < eShips.length; j++) {
            let otherShip = Array.from(eShips[j]);
            for (let k = 0; k < otherShip.length; k++) {
                let op = otherShip[k];
                let opx = getX(op);
                let opy = getY(op);
                // Ships cannot touch each other, not even adjacently (including diagonals)
                if (Math.abs(px - opx) <= 1 && Math.abs(py - opy) <= 1) {
                    return false;
                }
            }
        }
    }
    return true;
}

/**
 * Places a ship on the grid.
 * @param {number} x - Grid X.
 * @param {number} y - Grid Y.
 * @param {Object} currentShipCoords - Relative coordinates of the ship.
 * @param {Object} existingShips - List of existing ships.
 * @returns {Object} - The absolute coordinates if legal, null otherwise.
 */
function placeShip(x, y, currentShipCoords, existingShips) {
    const relCoords = Array.from(currentShipCoords);
    const absCoords = relCoords.map(p => ({ x: x + getX(p), y: y + getY(p) }));
    
    if (isPlacementLegal(absCoords, existingShips)) {
        return absCoords;
    }
    return null;
}

// Export functions for GraalVM
({
    rotateShip: rotateShip,
    isPlacementLegal: isPlacementLegal,
    placeShip: placeShip
});
