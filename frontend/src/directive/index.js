// Register the RuoYi operation-permission directive for business pages.
import hasPermi from "./permission/hasPermi";
export default (app) => app.directive("hasPermi", hasPermi);
