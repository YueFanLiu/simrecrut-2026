// Keep operation keys independent of the router and navigation component graph.
export const permissionFor = (id) =>
  ({
    A01: "system:user:list",
    A02: "system:role:list",
    C03: "simrecrut:resume:list",
    C04: "simrecrut:resume:list",
  })[id] || `simrecrut:${id.toLowerCase()}:view`;
