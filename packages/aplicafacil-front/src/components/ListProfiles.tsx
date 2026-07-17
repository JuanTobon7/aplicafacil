import { ProfileResponse } from '../types/profile';

interface ProfilesComponentProps {
  profileBank: ProfileResponse[];
  onSelectProfile: (profileId: string) => void;
}

function ProfileItem({
  profile,
  onSelect,
}: {
  profile: ProfileResponse;
  onSelect: (profileId: string) => void;
}) {
  return (
    <li>
      <button
        type="button"
        onClick={() => onSelect(profile.id)}
        className="w-full rounded-xl border border-gray-200 bg-white p-4 text-left transition-all hover:border-blue-500 hover:bg-blue-50 hover:shadow-md"
      >
        <div className="flex items-start justify-between gap-4">
          <div className="min-w-0">
            <h3 className="truncate text-lg font-semibold text-gray-900">
              {profile.title}
            </h3>

            <p className="mt-2 line-clamp-3 text-sm text-gray-600">
              {profile.summary}
            </p>
          </div>

          <span className="rounded-full bg-blue-100 px-3 py-1 text-xs font-medium text-blue-700">
            Abrir
          </span>
        </div>
      </button>
    </li>
  );
}

export function ProfilesList({
  profileBank,
  onSelectProfile,
}: ProfilesComponentProps) {
  return (
    <section className="rounded-2xl border border-gray-200 bg-white p-6 shadow-sm">
      <div className="mb-5 flex items-center justify-between">
        <div>
          <h2 className="text-xl font-bold text-gray-900">
            Perfiles guardados
          </h2>

          <p className="mt-1 text-sm text-gray-500">
            Selecciona un perfil para cargar su información.
          </p>
        </div>

        <span className="rounded-full bg-gray-100 px-3 py-1 text-sm font-medium text-gray-700">
          {profileBank.length}
        </span>
      </div>

      {profileBank.length === 0 ? (
        <div className="rounded-xl border border-dashed border-gray-300 py-10 text-center">
          <p className="text-gray-500">
            Aún no tienes perfiles guardados.
          </p>
        </div>
      ) : (
        <ul className="space-y-3">
          {profileBank.map((profile) => (
            <ProfileItem
              key={profile.id}
              profile={profile}
              onSelect={onSelectProfile}
            />
          ))}
        </ul>
      )}
    </section>
  );
}