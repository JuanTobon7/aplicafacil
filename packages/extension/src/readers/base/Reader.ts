import { JobForm } from "../../core/types/forms";

export interface Reader {
    readContent(): JobForm;
    available(): boolean;
}