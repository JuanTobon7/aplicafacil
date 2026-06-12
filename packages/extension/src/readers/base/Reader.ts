import { JobForm } from "../../types/forms";

export interface Reader {
    readContent(): JobForm;
    available(): boolean;
}