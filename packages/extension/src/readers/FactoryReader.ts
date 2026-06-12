import { Reader } from "./base/Reader";
import { GeneralContentReader } from "./impl/GeneralContentReader";
import { LinkedinReader } from "./impl/LinkedinReader";

export class ReaderFactory {

    private static readonly knownReaders: Record<string, Reader> = {
        linkedin: new LinkedinReader()
    };

    static getReader(site: string): Reader {

        return (
            this.knownReaders[site]
            ?? new GeneralContentReader()
        );

    }

}